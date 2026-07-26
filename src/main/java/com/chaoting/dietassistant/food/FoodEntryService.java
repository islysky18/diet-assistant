package com.chaoting.dietassistant.food;

import com.chaoting.dietassistant.profile.CurrentProfileProvider;
import com.chaoting.dietassistant.profile.ProfileResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class FoodEntryService {

    private static final int MULTIPLIER_SCALE = 8;
    private static final int NUTRITION_SCALE = 2;
    private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;

    private final FoodEntryRepository foodEntryRepository;
    private final SavedFoodRepository savedFoodRepository;
    private final CurrentProfileProvider currentProfileProvider;
    private final Clock clock;

    public FoodEntryService(
            FoodEntryRepository foodEntryRepository,
            SavedFoodRepository savedFoodRepository,
            CurrentProfileProvider currentProfileProvider,
            Clock clock
    ) {
        this.foodEntryRepository = foodEntryRepository;
        this.savedFoodRepository = savedFoodRepository;
        this.currentProfileProvider = currentProfileProvider;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public FoodEntryRequest newRequestForCurrentTime(Long savedFoodId) {
        FoodEntryRequest request = new FoodEntryRequest();
        request.setEatenAt(LocalDateTime.now(clock).withSecond(0).withNano(0));
        request.setSavedFoodId(savedFoodId);
        if (savedFoodId != null) {
            currentProfileProvider.getProfile()
                    .flatMap(profile -> savedFoodRepository.findByIdAndProfileIdAndActiveTrue(savedFoodId, profile.id()))
                    .ifPresent(savedFood -> {
                        request.setAmount(savedFood.getReferenceAmount());
                        request.setUnit(savedFood.getReferenceUnit());
                    });
        }
        return request;
    }

    @Transactional(readOnly = true)
    public Optional<String> validateCreate(FoodEntryRequest request) {
        ProfileResponse profile = requireProfile();
        Optional<SavedFood> savedFood = savedFoodRepository.findByIdAndProfileIdAndActiveTrue(
                request.getSavedFoodId(),
                profile.id()
        );
        if (savedFood.isEmpty()) {
            return Optional.of("Select an active saved food.");
        }
        if (!isCompatibleUnit(savedFood.get(), request.getUnit())) {
            return Optional.of("Use the saved food reference unit, or a supported weight unit when a reference weight is saved.");
        }
        return Optional.empty();
    }

    @Transactional(readOnly = true)
    public List<FoodEntryResponse> listTodayEntries() {
        return listEntriesForDate(LocalDate.now(clock));
    }

    @Transactional(readOnly = true)
    public List<FoodEntryResponse> listEntriesForDate(LocalDate date) {
        return currentProfileProvider.getProfile()
                .map(profile -> foodEntryRepository.findByProfileIdAndEatenAtGreaterThanEqualAndEatenAtLessThanOrderByEatenAtDescIdDesc(
                                profile.id(),
                                date.atStartOfDay(),
                                date.plusDays(1).atStartOfDay()
                        ).stream()
                        .map(this::toResponse)
                        .toList())
                .orElseGet(List::of);
    }

    @Transactional(readOnly = true)
    public DailyNutritionTotalsResponse todayTotals() {
        return totalsForDate(LocalDate.now(clock));
    }

    @Transactional(readOnly = true)
    public DailyNutritionTotalsResponse totalsForDate(LocalDate date) {
        return calculateTotals(listEntriesForDate(date));
    }

    public DailyNutritionTotalsResponse calculateTotals(List<FoodEntryResponse> entries) {
        BigDecimal calories = BigDecimal.ZERO;
        BigDecimal proteinGrams = BigDecimal.ZERO;
        BigDecimal carbohydrateGrams = BigDecimal.ZERO;
        BigDecimal fatGrams = BigDecimal.ZERO;
        BigDecimal fiberGrams = BigDecimal.ZERO;

        for (FoodEntryResponse entry : entries) {
            calories = calories.add(entry.calories());
            proteinGrams = proteinGrams.add(entry.proteinGrams());
            carbohydrateGrams = carbohydrateGrams.add(entry.carbohydrateGrams());
            fatGrams = fatGrams.add(entry.fatGrams());
            fiberGrams = fiberGrams.add(entry.fiberGrams());
        }

        return new DailyNutritionTotalsResponse(
                calories,
                proteinGrams,
                carbohydrateGrams,
                fatGrams,
                fiberGrams
        );
    }

    @Transactional
    public FoodEntryResponse create(FoodEntryRequest request) {
        ProfileResponse profile = requireProfile();
        SavedFood savedFood = savedFoodRepository.findByIdAndProfileIdAndActiveTrue(request.getSavedFoodId(), profile.id())
                .orElseThrow(() -> new IllegalArgumentException("Select an active saved food."));
        BigDecimal multiplier = calculateMultiplier(savedFood, request.getAmount(), request.getUnit());
        FoodEntry foodEntry = new FoodEntry();
        foodEntry.setProfileId(profile.id());
        foodEntry.setSavedFoodId(savedFood.getId());
        foodEntry.setSavedFoodName(savedFood.getName());
        foodEntry.setSavedFoodBrand(savedFood.getBrand());
        foodEntry.setSavedFoodReferenceAmount(savedFood.getReferenceAmount());
        foodEntry.setSavedFoodReferenceUnit(savedFood.getReferenceUnit());
        foodEntry.setSavedFoodReferenceWeightGrams(savedFood.getReferenceWeightGrams());
        foodEntry.setFoodName(savedFood.getName());
        foodEntry.setAmount(request.getAmount());
        foodEntry.setUnit(FoodUnit.canonicalize(request.getUnit()));
        foodEntry.setCalories(calculateNutrition(savedFood.getCalories(), multiplier));
        foodEntry.setProteinGrams(calculateNutrition(savedFood.getProteinGrams(), multiplier));
        foodEntry.setCarbohydrateGrams(calculateNutrition(savedFood.getCarbohydrateGrams(), multiplier));
        foodEntry.setFatGrams(calculateNutrition(savedFood.getFatGrams(), multiplier));
        foodEntry.setFiberGrams(calculateNutrition(savedFood.getFiberGrams(), multiplier));
        foodEntry.setCalculationMultiplier(multiplier);
        foodEntry.setMealType(request.getMealType());
        foodEntry.setEatenAt(request.getEatenAt());
        foodEntry.setNotes(blankToNull(request.getNotes()));
        foodEntry.setCreatedAt(LocalDateTime.now(clock));
        return toResponse(foodEntryRepository.save(foodEntry));
    }

    @Transactional(readOnly = true)
    public FoodEntryResponse getForEdit(Long id) {
        return toResponse(requireCurrentProfileEntry(id));
    }

    public FoodEntryEditRequest toEditRequest(FoodEntryResponse foodEntry) {
        FoodEntryEditRequest request = new FoodEntryEditRequest();
        request.setAmount(foodEntry.amount());
        request.setUnit(foodEntry.unit());
        request.setMealType(foodEntry.mealType());
        request.setEatenAt(foodEntry.eatenAt());
        request.setNotes(foodEntry.notes());
        return request;
    }

    @Transactional(readOnly = true)
    public Optional<EditValidationError> validateUpdate(Long id, FoodEntryEditRequest request) {
        FoodEntry foodEntry = requireCurrentProfileEntry(id);
        if (!amountOrUnitChanged(foodEntry, request)) {
            return Optional.empty();
        }
        if (!hasSafeRecalculationSnapshot(foodEntry)) {
            return Optional.of(new EditValidationError(
                    "amount",
                    "Amount and unit cannot be changed because this legacy entry does not contain a complete nutrition snapshot."
            ));
        }
        if (!isCompatibleUnit(foodEntry, request.getUnit())) {
            return Optional.of(new EditValidationError(
                    "unit",
                    "Use the stored reference unit, or a supported weight unit when a reference weight is saved."
            ));
        }
        return Optional.empty();
    }

    @Transactional
    public FoodEntryResponse update(Long id, FoodEntryEditRequest request) {
        FoodEntry foodEntry = requireCurrentProfileEntry(id);
        if (amountOrUnitChanged(foodEntry, request)) {
            if (!hasSafeRecalculationSnapshot(foodEntry)) {
                throw new IllegalArgumentException(
                        "Amount and unit cannot be changed because this legacy entry does not contain a complete nutrition snapshot."
                );
            }
            BigDecimal newMultiplier = calculateMultiplier(foodEntry, request.getAmount(), request.getUnit());
            if (newMultiplier.compareTo(foodEntry.getCalculationMultiplier()) != 0) {
                foodEntry.setCalories(recalculateNutrition(foodEntry.getCalories(), foodEntry.getCalculationMultiplier(), newMultiplier));
                foodEntry.setProteinGrams(recalculateNutrition(foodEntry.getProteinGrams(), foodEntry.getCalculationMultiplier(), newMultiplier));
                foodEntry.setCarbohydrateGrams(recalculateNutrition(foodEntry.getCarbohydrateGrams(), foodEntry.getCalculationMultiplier(), newMultiplier));
                foodEntry.setFatGrams(recalculateNutrition(foodEntry.getFatGrams(), foodEntry.getCalculationMultiplier(), newMultiplier));
                foodEntry.setFiberGrams(recalculateNutrition(foodEntry.getFiberGrams(), foodEntry.getCalculationMultiplier(), newMultiplier));
            }
            foodEntry.setAmount(request.getAmount());
            foodEntry.setUnit(FoodUnit.canonicalize(request.getUnit()));
            foodEntry.setCalculationMultiplier(newMultiplier);
        }
        foodEntry.setMealType(request.getMealType());
        foodEntry.setEatenAt(request.getEatenAt());
        foodEntry.setNotes(blankToNull(request.getNotes()));
        return toResponse(foodEntryRepository.save(foodEntry));
    }

    @Transactional
    public void delete(Long id) {
        ProfileResponse profile = requireProfile();
        foodEntryRepository.findByIdAndProfileId(id, profile.id())
                .ifPresent(foodEntryRepository::delete);
    }

    private BigDecimal calculateMultiplier(SavedFood savedFood, BigDecimal amount, String unit) {
        if (FoodUnit.equivalent(savedFood.getReferenceUnit(), unit)) {
            return amount.divide(savedFood.getReferenceAmount(), MULTIPLIER_SCALE, ROUNDING_MODE);
        }
        if (FoodUnit.isWeight(unit)) {
            BigDecimal referenceWeight = savedFood.getReferenceWeightGrams();
            if (referenceWeight == null && FoodUnit.isWeight(savedFood.getReferenceUnit())) {
                referenceWeight = FoodUnit.toGrams(savedFood.getReferenceAmount(), savedFood.getReferenceUnit());
            }
            if (referenceWeight != null && referenceWeight.signum() > 0) {
                return FoodUnit.toGrams(amount, unit).divide(referenceWeight, MULTIPLIER_SCALE, ROUNDING_MODE);
            }
        }
        throw new IllegalArgumentException(
                "Use the saved food reference unit, or a supported weight unit when a reference weight is saved."
        );
    }

    private BigDecimal calculateMultiplier(FoodEntry foodEntry, BigDecimal amount, String unit) {
        if (FoodUnit.equivalent(foodEntry.getSavedFoodReferenceUnit(), unit)) {
            return amount.divide(foodEntry.getSavedFoodReferenceAmount(), MULTIPLIER_SCALE, ROUNDING_MODE);
        }
        if (FoodUnit.isWeight(unit)) {
            BigDecimal referenceWeight = foodEntry.getSavedFoodReferenceWeightGrams();
            if (!hasPositiveReferenceWeight(foodEntry)
                    && FoodUnit.isWeight(foodEntry.getSavedFoodReferenceUnit())) {
                referenceWeight = FoodUnit.toGrams(
                        foodEntry.getSavedFoodReferenceAmount(),
                        foodEntry.getSavedFoodReferenceUnit()
                );
            }
            if (referenceWeight != null && referenceWeight.signum() > 0) {
                return FoodUnit.toGrams(amount, unit).divide(referenceWeight, MULTIPLIER_SCALE, ROUNDING_MODE);
            }
        }
        throw new IllegalArgumentException(
                "Use the stored reference unit, or a supported weight unit when a reference weight is saved."
        );
    }

    private BigDecimal calculateNutrition(BigDecimal referenceNutrition, BigDecimal multiplier) {
        return referenceNutrition.multiply(multiplier).setScale(NUTRITION_SCALE, ROUNDING_MODE);
    }

    private BigDecimal recalculateNutrition(
            BigDecimal storedNutrition,
            BigDecimal storedMultiplier,
            BigDecimal newMultiplier
    ) {
        return storedNutrition
                .multiply(newMultiplier)
                .divide(storedMultiplier, NUTRITION_SCALE, ROUNDING_MODE);
    }

    private boolean isCompatibleUnit(SavedFood savedFood, String unit) {
        return FoodUnit.equivalent(savedFood.getReferenceUnit(), unit)
                || (FoodUnit.isWeight(unit)
                && (savedFood.getReferenceWeightGrams() != null
                || FoodUnit.isWeight(savedFood.getReferenceUnit())));
    }

    private boolean isCompatibleUnit(FoodEntry foodEntry, String unit) {
        return FoodUnit.equivalent(foodEntry.getSavedFoodReferenceUnit(), unit)
                || (FoodUnit.isWeight(unit)
                && (hasPositiveReferenceWeight(foodEntry)
                || FoodUnit.isWeight(foodEntry.getSavedFoodReferenceUnit())));
    }

    private boolean amountOrUnitChanged(FoodEntry foodEntry, FoodEntryEditRequest request) {
        boolean amountChanged = request.getAmount() != null
                && foodEntry.getAmount().compareTo(request.getAmount()) != 0;
        boolean unitChanged = request.getUnit() != null
                && !FoodUnit.equivalent(foodEntry.getUnit(), request.getUnit());
        return amountChanged || unitChanged;
    }

    private boolean hasSafeRecalculationSnapshot(FoodEntry foodEntry) {
        if (foodEntry.getSavedFoodReferenceAmount() == null
                || foodEntry.getSavedFoodReferenceAmount().signum() <= 0
                || foodEntry.getSavedFoodReferenceUnit() == null
                || foodEntry.getSavedFoodReferenceUnit().isBlank()
                || foodEntry.getCalculationMultiplier() == null
                || foodEntry.getCalculationMultiplier().signum() <= 0) {
            return false;
        }
        try {
            BigDecimal expectedMultiplier = calculateMultiplier(
                    foodEntry,
                    foodEntry.getAmount(),
                    foodEntry.getUnit()
            );
            return expectedMultiplier.compareTo(foodEntry.getCalculationMultiplier()) == 0;
        } catch (IllegalArgumentException | ArithmeticException exception) {
            return false;
        }
    }

    private boolean hasPositiveReferenceWeight(FoodEntry foodEntry) {
        return foodEntry.getSavedFoodReferenceWeightGrams() != null
                && foodEntry.getSavedFoodReferenceWeightGrams().signum() > 0;
    }

    private ProfileResponse requireProfile() {
        return currentProfileProvider.getProfile()
                .orElseThrow(() -> new IllegalStateException("Create the user profile before recording food entries."));
    }

    private FoodEntry requireCurrentProfileEntry(Long id) {
        ProfileResponse profile = requireProfile();
        return foodEntryRepository.findByIdAndProfileId(id, profile.id())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND));
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private FoodEntryResponse toResponse(FoodEntry foodEntry) {
        return new FoodEntryResponse(
                foodEntry.getId(),
                foodEntry.getSavedFoodId(),
                foodEntry.getSavedFoodName(),
                foodEntry.getSavedFoodBrand(),
                foodEntry.getSavedFoodReferenceAmount(),
                foodEntry.getSavedFoodReferenceUnit(),
                foodEntry.getSavedFoodReferenceWeightGrams(),
                foodEntry.getFoodName(),
                foodEntry.getAmount(),
                foodEntry.getUnit(),
                foodEntry.getCalories(),
                foodEntry.getProteinGrams(),
                foodEntry.getCarbohydrateGrams(),
                foodEntry.getFatGrams(),
                foodEntry.getFiberGrams(),
                foodEntry.getCalculationMultiplier(),
                foodEntry.getMealType(),
                foodEntry.getEatenAt(),
                foodEntry.getNotes(),
                foodEntry.getCreatedAt()
        );
    }

    public record EditValidationError(String field, String message) {
    }
}

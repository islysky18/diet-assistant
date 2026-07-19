package com.chaoting.dietassistant.food;

import com.chaoting.dietassistant.profile.CurrentProfileProvider;
import com.chaoting.dietassistant.profile.ProfileResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.List;
import java.util.Optional;

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
            return Optional.of("Use the saved food reference unit, or grams when a reference weight is saved.");
        }
        return Optional.empty();
    }

    @Transactional(readOnly = true)
    public List<FoodEntryResponse> listTodayEntries() {
        return currentProfileProvider.getProfile()
                .map(profile -> foodEntryRepository.findByProfileIdAndEatenAtGreaterThanEqualAndEatenAtLessThanOrderByEatenAtDescIdDesc(
                                profile.id(),
                                todayStart(),
                                tomorrowStart()
                        ).stream()
                        .map(this::toResponse)
                        .toList())
                .orElseGet(List::of);
    }

    @Transactional(readOnly = true)
    public DailyNutritionTotalsResponse todayTotals() {
        return calculateTotals(listTodayEntries());
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
        foodEntry.setUnit(request.getUnit().trim());
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

    @Transactional
    public void delete(Long id) {
        ProfileResponse profile = requireProfile();
        foodEntryRepository.findByIdAndProfileId(id, profile.id())
                .ifPresent(foodEntryRepository::delete);
    }

    private BigDecimal calculateMultiplier(SavedFood savedFood, BigDecimal amount, String unit) {
        if (isSameUnit(savedFood.getReferenceUnit(), unit)) {
            return amount.divide(savedFood.getReferenceAmount(), MULTIPLIER_SCALE, ROUNDING_MODE);
        }
        if (isGramUnit(unit) && savedFood.getReferenceWeightGrams() != null) {
            return amount.divide(savedFood.getReferenceWeightGrams(), MULTIPLIER_SCALE, ROUNDING_MODE);
        }
        throw new IllegalArgumentException("Use the saved food reference unit, or grams when a reference weight is saved.");
    }

    private BigDecimal calculateNutrition(BigDecimal referenceNutrition, BigDecimal multiplier) {
        return referenceNutrition.multiply(multiplier).setScale(NUTRITION_SCALE, ROUNDING_MODE);
    }

    private boolean isCompatibleUnit(SavedFood savedFood, String unit) {
        return isSameUnit(savedFood.getReferenceUnit(), unit)
                || (isGramUnit(unit) && savedFood.getReferenceWeightGrams() != null);
    }

    private boolean isSameUnit(String first, String second) {
        return normalizeUnit(first).equals(normalizeUnit(second));
    }

    private String normalizeUnit(String unit) {
        String normalized = unit == null ? "" : unit.trim().toLowerCase(Locale.ROOT);
        if (isGramUnit(normalized)) {
            return "g";
        }
        return normalized;
    }

    private boolean isGramUnit(String unit) {
        String normalized = unit == null ? "" : unit.trim().toLowerCase(Locale.ROOT);
        return normalized.equals("g") || normalized.equals("gram") || normalized.equals("grams");
    }

    private LocalDateTime todayStart() {
        return LocalDate.now(clock).atStartOfDay();
    }

    private LocalDateTime tomorrowStart() {
        return LocalDate.now(clock).plusDays(1).atStartOfDay();
    }

    private ProfileResponse requireProfile() {
        return currentProfileProvider.getProfile()
                .orElseThrow(() -> new IllegalStateException("Create the user profile before recording food entries."));
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
}

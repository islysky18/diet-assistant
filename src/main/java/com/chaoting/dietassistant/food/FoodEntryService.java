package com.chaoting.dietassistant.food;

import com.chaoting.dietassistant.profile.CurrentProfileProvider;
import com.chaoting.dietassistant.profile.ProfileResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class FoodEntryService {

    private final FoodEntryRepository foodEntryRepository;
    private final CurrentProfileProvider currentProfileProvider;
    private final Clock clock;

    public FoodEntryService(
            FoodEntryRepository foodEntryRepository,
            CurrentProfileProvider currentProfileProvider,
            Clock clock
    ) {
        this.foodEntryRepository = foodEntryRepository;
        this.currentProfileProvider = currentProfileProvider;
        this.clock = clock;
    }

    public FoodEntryRequest newRequestForCurrentTime() {
        FoodEntryRequest request = new FoodEntryRequest();
        request.setEatenAt(LocalDateTime.now(clock).withSecond(0).withNano(0));
        return request;
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
        FoodEntry foodEntry = new FoodEntry();
        foodEntry.setProfileId(profile.id());
        foodEntry.setFoodName(request.getFoodName().trim());
        foodEntry.setAmount(request.getAmount());
        foodEntry.setUnit(request.getUnit().trim());
        foodEntry.setCalories(request.getCalories());
        foodEntry.setProteinGrams(request.getProteinGrams());
        foodEntry.setCarbohydrateGrams(request.getCarbohydrateGrams());
        foodEntry.setFatGrams(request.getFatGrams());
        foodEntry.setFiberGrams(request.getFiberGrams());
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
                foodEntry.getFoodName(),
                foodEntry.getAmount(),
                foodEntry.getUnit(),
                foodEntry.getCalories(),
                foodEntry.getProteinGrams(),
                foodEntry.getCarbohydrateGrams(),
                foodEntry.getFatGrams(),
                foodEntry.getFiberGrams(),
                foodEntry.getMealType(),
                foodEntry.getEatenAt(),
                foodEntry.getNotes(),
                foodEntry.getCreatedAt()
        );
    }
}

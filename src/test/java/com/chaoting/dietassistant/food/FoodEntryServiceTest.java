package com.chaoting.dietassistant.food;

import com.chaoting.dietassistant.profile.ProfileResponse;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FoodEntryServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-06-22T10:15:30Z"),
            ZoneOffset.UTC
    );

    @Test
    void newRequestForCurrentTimeDefaultsEatenAtAndSelectedFoodServing() {
        FakeSavedFoodRepository savedFoodRepository = new FakeSavedFoodRepository();
        savedFoodRepository.activeSavedFood = Optional.of(savedFood("Greek yogurt", "Chobani", "1.00", "cup", null));
        FoodEntryService foodEntryService = service(new FakeFoodEntryRepository(), savedFoodRepository);

        FoodEntryRequest request = foodEntryService.newRequestForCurrentTime(10L);

        assertThat(request.getEatenAt()).isEqualTo(LocalDateTime.of(2026, 6, 22, 10, 15));
        assertThat(request.getSavedFoodId()).isEqualTo(10L);
        assertThat(request.getAmount()).isEqualByComparingTo("1.00");
        assertThat(request.getUnit()).isEqualTo("cup");
    }

    @Test
    void createCalculatesNutritionFromSameUnitAndStoresSnapshots() {
        FakeFoodEntryRepository foodEntryRepository = new FakeFoodEntryRepository();
        FakeSavedFoodRepository savedFoodRepository = new FakeSavedFoodRepository();
        savedFoodRepository.activeSavedFood = Optional.of(savedFood("Greek yogurt", "Chobani", "1.00", "cup", null));
        FoodEntryService foodEntryService = service(foodEntryRepository, savedFoodRepository);

        FoodEntryResponse response = foodEntryService.create(request(10L, "1.50", " cup ", MealType.BREAKFAST));

        assertThat(foodEntryRepository.savedEntry().getProfileId()).isEqualTo(7L);
        assertThat(response.savedFoodId()).isEqualTo(10L);
        assertThat(response.savedFoodName()).isEqualTo("Greek yogurt");
        assertThat(response.savedFoodBrand()).isEqualTo("Chobani");
        assertThat(response.foodName()).isEqualTo("Greek yogurt");
        assertThat(response.amount()).isEqualByComparingTo("1.50");
        assertThat(response.unit()).isEqualTo("cup");
        assertThat(response.calculationMultiplier()).isEqualByComparingTo("1.50000000");
        assertThat(response.calories()).isEqualByComparingTo("150.00");
        assertThat(response.proteinGrams()).isEqualByComparingTo("15.00");
        assertThat(response.carbohydrateGrams()).isEqualByComparingTo("7.50");
        assertThat(response.fatGrams()).isEqualByComparingTo("3.00");
        assertThat(response.fiberGrams()).isEqualByComparingTo("0.00");
        assertThat(response.createdAt()).isEqualTo(LocalDateTime.of(2026, 6, 22, 10, 15, 30));
    }

    @Test
    void createAllowsGramAliasesWhenReferenceWeightExists() {
        FakeFoodEntryRepository foodEntryRepository = new FakeFoodEntryRepository();
        FakeSavedFoodRepository savedFoodRepository = new FakeSavedFoodRepository();
        savedFoodRepository.activeSavedFood = Optional.of(savedFood("Bread", "Bakery", "1.00", "slice", "40.00"));
        FoodEntryService foodEntryService = service(foodEntryRepository, savedFoodRepository);

        FoodEntryResponse response = foodEntryService.create(request(10L, "60.00", "Grams", MealType.SNACK));

        assertThat(response.calculationMultiplier()).isEqualByComparingTo("1.50000000");
        assertThat(response.calories()).isEqualByComparingTo("150.00");
        assertThat(response.unit()).isEqualTo("Grams");
    }

    @Test
    void validateCreateRejectsIncompatibleUnit() {
        FakeSavedFoodRepository savedFoodRepository = new FakeSavedFoodRepository();
        savedFoodRepository.activeSavedFood = Optional.of(savedFood("Soup", null, "1.00", "bowl", null));
        FoodEntryService foodEntryService = service(new FakeFoodEntryRepository(), savedFoodRepository);

        Optional<String> validationMessage = foodEntryService.validateCreate(request(10L, "100.00", "g", MealType.LUNCH));

        assertThat(validationMessage).contains("Use the saved food reference unit, or grams when a reference weight is saved.");
    }

    @Test
    void createRejectsInactiveOrMissingSavedFood() {
        FoodEntryService foodEntryService = service(new FakeFoodEntryRepository(), new FakeSavedFoodRepository());

        assertThatThrownBy(() -> foodEntryService.create(request(10L, "1.00", "serving", MealType.SNACK)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Select an active saved food.");
    }

    @Test
    void listEntriesForDateUsesSelectedDayBoundariesAndRepositoryOrder() {
        FakeFoodEntryRepository foodEntryRepository = new FakeFoodEntryRepository();
        FoodEntry first = legacyEntry("Lunch", "500.00", LocalDateTime.of(2026, 6, 22, 12, 30));
        FoodEntry second = legacyEntry("Breakfast", "300.00", LocalDateTime.of(2026, 6, 22, 8, 0));
        foodEntryRepository.entries = List.of(first, second);
        FoodEntryService foodEntryService = service(foodEntryRepository, new FakeSavedFoodRepository());

        List<FoodEntryResponse> responses = foodEntryService.listEntriesForDate(LocalDate.of(2026, 6, 22));

        assertThat(foodEntryRepository.profileIdForList).isEqualTo(7L);
        assertThat(foodEntryRepository.startInclusive).isEqualTo(LocalDateTime.of(2026, 6, 22, 0, 0));
        assertThat(foodEntryRepository.endExclusive).isEqualTo(LocalDateTime.of(2026, 6, 23, 0, 0));
        assertThat(responses).extracting(FoodEntryResponse::foodName).containsExactly("Lunch", "Breakfast");
    }

    @Test
    void calculateTotalsSumsStoredSnapshotsAndReturnsZeroForEmptyEntries() {
        FoodEntryService foodEntryService = service(new FakeFoodEntryRepository(), new FakeSavedFoodRepository());
        DailyNutritionTotalsResponse totals = foodEntryService.calculateTotals(List.of(
                response("300.00", "20.00", "30.00", "10.00", "5.00"),
                response("100.00", "5.00", "10.00", "4.00", "2.00")
        ));

        assertThat(totals.calories()).isEqualByComparingTo("400.00");
        assertThat(totals.proteinGrams()).isEqualByComparingTo("25.00");
        assertThat(totals.carbohydrateGrams()).isEqualByComparingTo("40.00");
        assertThat(totals.fatGrams()).isEqualByComparingTo("14.00");
        assertThat(totals.fiberGrams()).isEqualByComparingTo("7.00");
        assertThat(foodEntryService.calculateTotals(List.of())).isEqualTo(DailyNutritionTotalsResponse.zero());
    }

    @Test
    void savedFoodEditsDoNotChangeCreatedEntrySnapshots() {
        FakeFoodEntryRepository foodEntryRepository = new FakeFoodEntryRepository();
        FakeSavedFoodRepository savedFoodRepository = new FakeSavedFoodRepository();
        SavedFood savedFood = savedFood("Original", "Brand", "1.00", "serving", null);
        savedFoodRepository.activeSavedFood = Optional.of(savedFood);
        FoodEntryService foodEntryService = service(foodEntryRepository, savedFoodRepository);

        foodEntryService.create(request(10L, "1.00", "serving", MealType.DINNER));
        savedFood.setName("Edited");
        savedFood.setCalories(new BigDecimal("999.00"));

        assertThat(foodEntryRepository.savedEntry().getFoodName()).isEqualTo("Original");
        assertThat(foodEntryRepository.savedEntry().getCalories()).isEqualByComparingTo("100.00");
    }

    @Test
    void deleteUsesProfileScopedLookup() {
        FakeFoodEntryRepository foodEntryRepository = new FakeFoodEntryRepository();
        FoodEntry foodEntry = legacyEntry("Snack", "100.00", LocalDateTime.of(2026, 6, 22, 15, 0));
        foodEntryRepository.entryByIdAndProfile = Optional.of(foodEntry);
        FoodEntryService foodEntryService = service(foodEntryRepository, new FakeSavedFoodRepository());

        foodEntryService.delete(10L);

        assertThat(foodEntryRepository.idForDeleteLookup).isEqualTo(10L);
        assertThat(foodEntryRepository.profileIdForDeleteLookup).isEqualTo(7L);
        assertThat(foodEntryRepository.deletedEntry).isSameAs(foodEntry);
    }

    @Test
    void createRequiresProfile() {
        FoodEntryService foodEntryService = new FoodEntryService(
                new FakeFoodEntryRepository().proxy(),
                new FakeSavedFoodRepository().proxy(),
                Optional::empty,
                FIXED_CLOCK
        );

        assertThatThrownBy(() -> foodEntryService.create(request(10L, "1.00", "serving", MealType.SNACK)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Create the user profile before recording food entries.");
    }

    private FoodEntryService service(FakeFoodEntryRepository foodEntryRepository, FakeSavedFoodRepository savedFoodRepository) {
        return new FoodEntryService(
                foodEntryRepository.proxy(),
                savedFoodRepository.proxy(),
                () -> Optional.of(profile()),
                FIXED_CLOCK
        );
    }

    private FoodEntryRequest request(Long savedFoodId, String amount, String unit, MealType mealType) {
        FoodEntryRequest request = new FoodEntryRequest();
        request.setSavedFoodId(savedFoodId);
        request.setAmount(new BigDecimal(amount));
        request.setUnit(unit);
        request.setMealType(mealType);
        request.setEatenAt(LocalDateTime.of(2026, 6, 22, 12, 0));
        request.setNotes("  Notes  ");
        return request;
    }

    private ProfileResponse profile() {
        return new ProfileResponse(
                7L,
                1988,
                new BigDecimal("175.50"),
                new BigDecimal("72.25"),
                null,
                3,
                LocalTime.of(18, 30),
                null
        );
    }

    private SavedFood savedFood(String name, String brand, String referenceAmount, String referenceUnit, String referenceWeightGrams) {
        SavedFood savedFood = new SavedFood();
        setId(savedFood, 10L);
        savedFood.setProfileId(7L);
        savedFood.setName(name);
        savedFood.setBrand(brand);
        savedFood.setReferenceAmount(new BigDecimal(referenceAmount));
        savedFood.setReferenceUnit(referenceUnit);
        savedFood.setReferenceWeightGrams(referenceWeightGrams == null ? null : new BigDecimal(referenceWeightGrams));
        savedFood.setCalories(new BigDecimal("100.00"));
        savedFood.setProteinGrams(new BigDecimal("10.00"));
        savedFood.setCarbohydrateGrams(new BigDecimal("5.00"));
        savedFood.setFatGrams(new BigDecimal("2.00"));
        savedFood.setFiberGrams(new BigDecimal("0.00"));
        savedFood.setActive(true);
        savedFood.setCreatedAt(LocalDateTime.of(2026, 6, 22, 10, 15, 30));
        savedFood.setUpdatedAt(LocalDateTime.of(2026, 6, 22, 10, 15, 30));
        return savedFood;
    }

    private void setId(SavedFood savedFood, Long id) {
        try {
            var field = SavedFood.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(savedFood, id);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to set saved food id for test.", exception);
        }
    }

    private FoodEntry legacyEntry(String foodName, String calories, LocalDateTime eatenAt) {
        FoodEntry foodEntry = new FoodEntry();
        foodEntry.setProfileId(7L);
        foodEntry.setFoodName(foodName);
        foodEntry.setAmount(new BigDecimal("1.00"));
        foodEntry.setUnit("serving");
        foodEntry.setCalories(new BigDecimal(calories));
        foodEntry.setProteinGrams(new BigDecimal("10.00"));
        foodEntry.setCarbohydrateGrams(new BigDecimal("20.00"));
        foodEntry.setFatGrams(new BigDecimal("5.00"));
        foodEntry.setFiberGrams(new BigDecimal("2.00"));
        foodEntry.setMealType(MealType.SNACK);
        foodEntry.setEatenAt(eatenAt);
        foodEntry.setCreatedAt(LocalDateTime.of(2026, 6, 22, 10, 15, 30));
        return foodEntry;
    }

    private FoodEntryResponse response(
            String calories,
            String proteinGrams,
            String carbohydrateGrams,
            String fatGrams,
            String fiberGrams
    ) {
        return new FoodEntryResponse(
                1L,
                null,
                null,
                null,
                null,
                null,
                null,
                "Food",
                new BigDecimal("1.00"),
                "serving",
                new BigDecimal(calories),
                new BigDecimal(proteinGrams),
                new BigDecimal(carbohydrateGrams),
                new BigDecimal(fatGrams),
                new BigDecimal(fiberGrams),
                null,
                MealType.SNACK,
                LocalDateTime.of(2026, 6, 22, 12, 0),
                null,
                LocalDateTime.of(2026, 6, 22, 12, 0)
        );
    }

    private static class FakeFoodEntryRepository {

        private List<FoodEntry> entries = List.of();
        private Optional<FoodEntry> entryByIdAndProfile = Optional.empty();
        private FoodEntry savedEntry;
        private FoodEntry deletedEntry;
        private Long profileIdForList;
        private LocalDateTime startInclusive;
        private LocalDateTime endExclusive;
        private Long idForDeleteLookup;
        private Long profileIdForDeleteLookup;

        FoodEntryRepository proxy() {
            return (FoodEntryRepository) Proxy.newProxyInstance(
                    FoodEntryRepository.class.getClassLoader(),
                    new Class<?>[]{FoodEntryRepository.class},
                    (proxy, method, args) -> {
                        if (method.getName().equals("save")) {
                            savedEntry = (FoodEntry) args[0];
                            return savedEntry;
                        }
                        if (method.getName().equals("findByProfileIdAndEatenAtGreaterThanEqualAndEatenAtLessThanOrderByEatenAtDescIdDesc")) {
                            profileIdForList = (Long) args[0];
                            startInclusive = (LocalDateTime) args[1];
                            endExclusive = (LocalDateTime) args[2];
                            return entries;
                        }
                        if (method.getName().equals("findByIdAndProfileId")) {
                            idForDeleteLookup = (Long) args[0];
                            profileIdForDeleteLookup = (Long) args[1];
                            return entryByIdAndProfile;
                        }
                        if (method.getName().equals("delete")) {
                            deletedEntry = (FoodEntry) args[0];
                            return null;
                        }
                        if (method.getName().equals("toString")) {
                            return "FakeFoodEntryRepository";
                        }
                        throw new UnsupportedOperationException(method.getName());
                    }
            );
        }

        FoodEntry savedEntry() {
            return savedEntry;
        }
    }

    private static class FakeSavedFoodRepository {

        private Optional<SavedFood> activeSavedFood = Optional.empty();

        SavedFoodRepository proxy() {
            return (SavedFoodRepository) Proxy.newProxyInstance(
                    SavedFoodRepository.class.getClassLoader(),
                    new Class<?>[]{SavedFoodRepository.class},
                    (proxy, method, args) -> {
                        if (method.getName().equals("findByIdAndProfileIdAndActiveTrue")) {
                            return activeSavedFood;
                        }
                        if (method.getName().equals("toString")) {
                            return "FakeSavedFoodRepository";
                        }
                        throw new UnsupportedOperationException(method.getName());
                    }
            );
        }
    }
}

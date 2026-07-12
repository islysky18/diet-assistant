package com.chaoting.dietassistant.food;

import com.chaoting.dietassistant.profile.ProfileResponse;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
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
    void newRequestForCurrentTimeDefaultsEatenAtFromClock() {
        FoodEntryService foodEntryService = new FoodEntryService(
                new FakeFoodEntryRepository().proxy(),
                Optional::empty,
                FIXED_CLOCK
        );

        FoodEntryRequest request = foodEntryService.newRequestForCurrentTime();

        assertThat(request.getEatenAt()).isEqualTo(LocalDateTime.of(2026, 6, 22, 10, 15));
    }

    @Test
    void createStoresEntryForCurrentProfileAndTrimsText() {
        FakeFoodEntryRepository foodEntryRepository = new FakeFoodEntryRepository();
        FoodEntryService foodEntryService = new FoodEntryService(
                foodEntryRepository.proxy(),
                () -> Optional.of(profile()),
                FIXED_CLOCK
        );

        FoodEntryResponse response = foodEntryService.create(request(
                "  Greek yogurt  ",
                "1.50",
                " cup ",
                "150.00",
                "20.00",
                "10.00",
                "2.00",
                "0.00",
                MealType.BREAKFAST,
                LocalDateTime.of(2026, 6, 22, 8, 0),
                "  With berries  "
        ));

        assertThat(foodEntryRepository.savedEntry().getProfileId()).isEqualTo(7L);
        assertThat(response.foodName()).isEqualTo("Greek yogurt");
        assertThat(response.unit()).isEqualTo("cup");
        assertThat(response.notes()).isEqualTo("With berries");
        assertThat(response.createdAt()).isEqualTo(LocalDateTime.of(2026, 6, 22, 10, 15, 30));
    }

    @Test
    void createConvertsBlankNotesToNullAndAcceptsZeroCalories() {
        FakeFoodEntryRepository foodEntryRepository = new FakeFoodEntryRepository();
        FoodEntryService foodEntryService = new FoodEntryService(
                foodEntryRepository.proxy(),
                () -> Optional.of(profile()),
                FIXED_CLOCK
        );

        FoodEntryResponse response = foodEntryService.create(request(
                "Water",
                "1.00",
                "glass",
                "0.00",
                "0.00",
                "0.00",
                "0.00",
                "0.00",
                MealType.SNACK,
                LocalDateTime.of(2026, 6, 22, 12, 0),
                "   "
        ));

        assertThat(response.calories()).isEqualByComparingTo("0.00");
        assertThat(response.notes()).isNull();
    }

    @Test
    void listTodayEntriesUsesCurrentDayBoundariesAndRepositoryOrder() {
        FakeFoodEntryRepository foodEntryRepository = new FakeFoodEntryRepository();
        FoodEntry first = entry("Lunch", "500.00", LocalDateTime.of(2026, 6, 22, 12, 30));
        FoodEntry second = entry("Breakfast", "300.00", LocalDateTime.of(2026, 6, 22, 8, 0));
        foodEntryRepository.entries = List.of(first, second);
        FoodEntryService foodEntryService = new FoodEntryService(
                foodEntryRepository.proxy(),
                () -> Optional.of(profile()),
                FIXED_CLOCK
        );

        List<FoodEntryResponse> responses = foodEntryService.listTodayEntries();

        assertThat(foodEntryRepository.profileIdForList).isEqualTo(7L);
        assertThat(foodEntryRepository.startInclusive).isEqualTo(LocalDateTime.of(2026, 6, 22, 0, 0));
        assertThat(foodEntryRepository.endExclusive).isEqualTo(LocalDateTime.of(2026, 6, 23, 0, 0));
        assertThat(responses).extracting(FoodEntryResponse::foodName).containsExactly("Lunch", "Breakfast");
    }

    @Test
    void listTodayEntriesReturnsEmptyListWhenProfileDoesNotExist() {
        FoodEntryService foodEntryService = new FoodEntryService(
                new FakeFoodEntryRepository().proxy(),
                Optional::empty,
                FIXED_CLOCK
        );

        List<FoodEntryResponse> responses = foodEntryService.listTodayEntries();

        assertThat(responses).isEmpty();
    }

    @Test
    void calculateTotalsSumsEntriesAndReturnsZeroForEmptyEntries() {
        FoodEntryService foodEntryService = new FoodEntryService(
                new FakeFoodEntryRepository().proxy(),
                Optional::empty,
                FIXED_CLOCK
        );
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
    void deleteUsesProfileScopedLookup() {
        FakeFoodEntryRepository foodEntryRepository = new FakeFoodEntryRepository();
        FoodEntry foodEntry = entry("Snack", "100.00", LocalDateTime.of(2026, 6, 22, 15, 0));
        foodEntryRepository.entryByIdAndProfile = Optional.of(foodEntry);
        FoodEntryService foodEntryService = new FoodEntryService(
                foodEntryRepository.proxy(),
                () -> Optional.of(profile()),
                FIXED_CLOCK
        );

        foodEntryService.delete(10L);

        assertThat(foodEntryRepository.idForDeleteLookup).isEqualTo(10L);
        assertThat(foodEntryRepository.profileIdForDeleteLookup).isEqualTo(7L);
        assertThat(foodEntryRepository.deletedEntry).isSameAs(foodEntry);
    }

    @Test
    void deleteDoesNotDeleteWhenProfileScopedEntryIsNotFound() {
        FakeFoodEntryRepository foodEntryRepository = new FakeFoodEntryRepository();
        FoodEntryService foodEntryService = new FoodEntryService(
                foodEntryRepository.proxy(),
                () -> Optional.of(profile()),
                FIXED_CLOCK
        );

        foodEntryService.delete(10L);

        assertThat(foodEntryRepository.deletedEntry).isNull();
    }

    @Test
    void createRequiresProfile() {
        FoodEntryService foodEntryService = new FoodEntryService(
                new FakeFoodEntryRepository().proxy(),
                Optional::empty,
                FIXED_CLOCK
        );

        assertThatThrownBy(() -> foodEntryService.create(request(
                "Apple",
                "1.00",
                "piece",
                "95.00",
                "0.50",
                "25.00",
                "0.30",
                "4.00",
                MealType.SNACK,
                LocalDateTime.of(2026, 6, 22, 12, 0),
                null
        ))).isInstanceOf(IllegalStateException.class)
                .hasMessage("Create the user profile before recording food entries.");
    }

    private FoodEntryRequest request(
            String foodName,
            String amount,
            String unit,
            String calories,
            String proteinGrams,
            String carbohydrateGrams,
            String fatGrams,
            String fiberGrams,
            MealType mealType,
            LocalDateTime eatenAt,
            String notes
    ) {
        FoodEntryRequest request = new FoodEntryRequest();
        request.setFoodName(foodName);
        request.setAmount(new BigDecimal(amount));
        request.setUnit(unit);
        request.setCalories(new BigDecimal(calories));
        request.setProteinGrams(new BigDecimal(proteinGrams));
        request.setCarbohydrateGrams(new BigDecimal(carbohydrateGrams));
        request.setFatGrams(new BigDecimal(fatGrams));
        request.setFiberGrams(new BigDecimal(fiberGrams));
        request.setMealType(mealType);
        request.setEatenAt(eatenAt);
        request.setNotes(notes);
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

    private FoodEntry entry(String foodName, String calories, LocalDateTime eatenAt) {
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
                "Food",
                new BigDecimal("1.00"),
                "serving",
                new BigDecimal(calories),
                new BigDecimal(proteinGrams),
                new BigDecimal(carbohydrateGrams),
                new BigDecimal(fatGrams),
                new BigDecimal(fiberGrams),
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
}

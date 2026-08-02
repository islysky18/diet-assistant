package com.chaoting.dietassistant.nutrition;

import com.chaoting.dietassistant.food.FoodEntryResponse;
import com.chaoting.dietassistant.food.FoodEntryService;
import com.chaoting.dietassistant.food.MealType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class WeeklyNutritionSummaryServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-26T18:00:00Z"), ZoneOffset.UTC);
    private StubFoodEntryService foodEntryService;
    private StubNutritionGoalService nutritionGoalService;
    private WeeklyNutritionSummaryService service;

    @BeforeEach
    void setUp() {
        foodEntryService = new StubFoodEntryService();
        nutritionGoalService = new StubNutritionGoalService();
        service = new WeeklyNutritionSummaryService(foodEntryService, nutritionGoalService, CLOCK);
    }

    @Test
    void normalizesAnyDateToMondayAndDefaultsToCurrentWeek() {
        assertThat(service.normalizeWeekStart(LocalDate.of(2026, 7, 23)))
                .isEqualTo(LocalDate.of(2026, 7, 20));
        stubEntries(List.of());

        WeeklyNutritionSummaryResponse summary = service.getSummary(null);

        assertThat(summary.weekStart()).isEqualTo(LocalDate.of(2026, 7, 20));
        assertThat(summary.weekEnd()).isEqualTo(LocalDate.of(2026, 7, 26));
        assertThat(summary.currentWeek()).isTrue();
        assertThat(summary.nextWeekStart()).isNull();
    }

    @Test
    void handlesCrossMonthAndCrossYearWeeks() {
        WeeklyNutritionSummaryResponse crossMonth = summaryWithEmptyEntries(LocalDate.of(2026, 7, 2));

        assertThat(crossMonth.weekStart()).isEqualTo(LocalDate.of(2026, 6, 29));
        assertThat(crossMonth.weekEnd()).isEqualTo(LocalDate.of(2026, 7, 5));
        assertThat(crossMonth.dateRangeLabel()).isEqualTo("Jun 29–Jul 5, 2026");
        assertThat(crossMonth.currentWeek()).isFalse();

        WeeklyNutritionSummaryResponse crossYear = summaryWithEmptyEntries(LocalDate.of(2025, 12, 31));

        assertThat(crossYear.weekStart()).isEqualTo(LocalDate.of(2025, 12, 29));
        assertThat(crossYear.weekEnd()).isEqualTo(LocalDate.of(2026, 1, 4));
        assertThat(crossYear.dateRangeLabel()).isEqualTo("Dec 29, 2025–Jan 4, 2026");
    }

    @Test
    void futureWeekFallsBackToCurrentWeek() {
        stubEntries(List.of());

        WeeklyNutritionSummaryResponse summary = service.getSummary(LocalDate.of(2026, 8, 8));

        assertThat(summary.weekStart()).isEqualTo(LocalDate.of(2026, 7, 20));
        assertThat(summary.currentWeek()).isTrue();
    }

    @Test
    void futureDateWithinCurrentWeekStillNormalizesToCurrentMonday() {
        Clock wednesday = Clock.fixed(Instant.parse("2026-07-22T18:00:00Z"), ZoneOffset.UTC);
        WeeklyNutritionSummaryService wednesdayService = new WeeklyNutritionSummaryService(
                foodEntryService,
                nutritionGoalService,
                wednesday
        );

        WeeklyNutritionSummaryResponse summary = wednesdayService.getSummary(LocalDate.of(2026, 7, 24));

        assertThat(summary.weekStart()).isEqualTo(LocalDate.of(2026, 7, 20));
        assertThat(summary.currentWeek()).isTrue();
    }

    @Test
    void calculatesSevenDayTotalsLoggedDaysAndFixedSevenDayAverages() {
        stubEntries(List.of(
                entry("Monday one", "2026-07-20T08:00:00", "700", "70", "140", "35"),
                entry("Monday zero", "2026-07-20T12:00:00", "0", "0", "0", "0"),
                entry("Wednesday", "2026-07-22T18:00:00", "701", "71", "141", "36")
        ));

        WeeklyNutritionSummaryResponse summary = service.getSummary(LocalDate.of(2026, 7, 20));

        assertThat(summary.daysLogged()).isEqualTo(2);
        assertThat(summary.foodEntryCount()).isEqualTo(3);
        assertThat(summary.weeklyCalories()).isEqualByComparingTo("1401");
        assertThat(summary.averageCalories()).isEqualByComparingTo("200");
        assertThat(summary.nutrientProgress().get(1).dailyAverage()).isEqualByComparingTo("20.1");
        assertThat(summary.dailyBreakdown()).hasSize(7);
        assertThat(summary.dailyBreakdown().getFirst().logged()).isTrue();
        assertThat(summary.dailyBreakdown().get(1).logged()).isFalse();
        assertThat(summary.dailyBreakdown().get(1).dayName()).isEqualTo("Tuesday");
    }

    @Test
    void comparesCurrentGoalsWithoutCappingProgress() {
        stubEntries(List.of(entry("Weekly total", "2026-07-20T08:00:00", "15680", "840", "1750", "490")));
        nutritionGoalService.goal = Optional.of(goal("2000", "120", "250", "70"));

        WeeklyNutritionSummaryResponse summary = service.getSummary(LocalDate.of(2026, 7, 20));

        assertThat(summary.averageCalories()).isEqualByComparingTo("2240");
        assertThat(summary.nutrientProgress().getFirst().percentage()).isEqualByComparingTo("112");
        assertThat(summary.nutrientProgress().get(1).dailyAverage()).isEqualByComparingTo("120.0");
        assertThat(summary.nutrientProgress().get(1).percentage()).isEqualByComparingTo("100");
    }

    @Test
    void calculatesProgressFromExactAverageBeforeDisplayRounding() {
        stubEntries(List.of(entry("Non-divisible", "2026-07-20T08:00:00", "703.43", "70.28", "0", "0")));
        nutritionGoalService.goal = Optional.of(goal("30", "3", "1", "1"));

        WeeklyNutritionSummaryResponse summary = service.getSummary(LocalDate.of(2026, 7, 20));

        WeeklyNutrientProgressResponse calories = summary.nutrientProgress().getFirst();
        assertThat(calories.dailyAverage()).isEqualByComparingTo("100");
        assertThat(calories.percentage()).isEqualByComparingTo("335");

        WeeklyNutrientProgressResponse protein = summary.nutrientProgress().get(1);
        assertThat(protein.dailyAverage()).isEqualByComparingTo("10.0");
        assertThat(protein.percentage()).isEqualByComparingTo("335");
    }

    @Test
    void handlesEachNutrientGoalIndependently() {
        stubEntries(List.of(entry("Entry", "2026-07-20T08:00:00", "700", "70", "140", "35")));
        nutritionGoalService.goal = Optional.of(goal("100", null, "0", "5"));

        WeeklyNutritionSummaryResponse summary = service.getSummary(LocalDate.of(2026, 7, 20));

        assertThat(summary.nutrientProgress().get(0).percentage()).isEqualByComparingTo("100");
        assertThat(summary.nutrientProgress().get(0).dailyGoal()).isEqualByComparingTo("100");
        assertThat(summary.nutrientProgress().get(1).dailyGoal()).isNull();
        assertThat(summary.nutrientProgress().get(1).percentage()).isNull();
        assertThat(summary.nutrientProgress().get(2).dailyGoal()).isNull();
        assertThat(summary.nutrientProgress().get(2).percentage()).isNull();
        assertThat(summary.nutrientProgress().get(3).percentage()).isEqualByComparingTo("100");
    }

    @Test
    void missingAndZeroGoalsAreNotSetAndNeverDividedByZero() {
        stubEntries(List.of(entry("Entry", "2026-07-20T08:00:00", "700", "70", "140", "35")));
        nutritionGoalService.goal = Optional.of(goal("0", "0", "0", "0"));

        WeeklyNutritionSummaryResponse summary = service.getSummary(LocalDate.of(2026, 7, 20));

        assertThat(summary.nutrientProgress())
                .allSatisfy(progress -> {
                    assertThat(progress.dailyGoal()).isNull();
                    assertThat(progress.percentage()).isNull();
                });
    }

    @Test
    void requestsExactlyTheCurrentProfileServicesMondayToNextMondayRange() {
        stubEntries(List.of());

        service.getSummary(LocalDate.of(2026, 7, 23));

        assertThat(foodEntryService.requestedStart).isEqualTo(LocalDate.of(2026, 7, 20));
        assertThat(foodEntryService.requestedEnd).isEqualTo(LocalDate.of(2026, 7, 27));
    }

    @Test
    void recordFoodUsesTodayForCurrentWeekAndMondayForPastWeek() {
        WeeklyNutritionSummaryResponse current = summaryWithEmptyEntries(LocalDate.of(2026, 7, 20));
        WeeklyNutritionSummaryResponse past = summaryWithEmptyEntries(LocalDate.of(2026, 7, 13));

        assertThat(current.recordFoodDate(LocalDate.of(2026, 7, 26))).isEqualTo(LocalDate.of(2026, 7, 26));
        assertThat(past.recordFoodDate(LocalDate.of(2026, 7, 26))).isEqualTo(LocalDate.of(2026, 7, 13));
    }

    private void stubEntries(List<FoodEntryResponse> entries) {
        foodEntryService.entries = entries;
    }

    private WeeklyNutritionSummaryResponse summaryWithEmptyEntries(LocalDate date) {
        stubEntries(List.of());
        return service.getSummary(date);
    }

    private FoodEntryResponse entry(
            String name,
            String eatenAt,
            String calories,
            String protein,
            String carbohydrate,
            String fat
    ) {
        return new FoodEntryResponse(
                1L, null, null, null, null, null, null, name,
                BigDecimal.ONE, "serving", new BigDecimal(calories), new BigDecimal(protein),
                new BigDecimal(carbohydrate), new BigDecimal(fat), BigDecimal.ZERO, BigDecimal.ONE,
                MealType.BREAKFAST, LocalDateTime.parse(eatenAt), null, LocalDateTime.parse(eatenAt)
        );
    }

    private NutritionGoalResponse goal(String calories, String protein, String carbohydrate, String fat) {
        return new NutritionGoalResponse(
                1L, 1L, decimal(calories), decimal(protein),
                decimal(carbohydrate), decimal(fat),
                LocalDateTime.of(2026, 7, 1, 9, 0), LocalDateTime.of(2026, 7, 1, 9, 0)
        );
    }

    private BigDecimal decimal(String value) {
        return value == null ? null : new BigDecimal(value);
    }

    private static final class StubFoodEntryService extends FoodEntryService {
        private List<FoodEntryResponse> entries = List.of();
        private LocalDate requestedStart;
        private LocalDate requestedEnd;

        private StubFoodEntryService() {
            super(null, null, null, CLOCK);
        }

        @Override
        public List<FoodEntryResponse> listEntriesForDateRange(LocalDate startInclusive, LocalDate endExclusive) {
            requestedStart = startInclusive;
            requestedEnd = endExclusive;
            return entries;
        }
    }

    private static final class StubNutritionGoalService extends NutritionGoalService {
        private Optional<NutritionGoalResponse> goal = Optional.empty();

        private StubNutritionGoalService() {
            super(null, null, CLOCK);
        }

        @Override
        public Optional<NutritionGoalResponse> getCurrentGoal() {
            return goal;
        }
    }
}

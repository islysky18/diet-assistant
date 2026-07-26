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
        assertThat(summaryWithEmptyEntries(LocalDate.of(2026, 7, 29)).weekStart())
                .isEqualTo(LocalDate.of(2026, 7, 20));

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
                1L, 1L, new BigDecimal(calories), new BigDecimal(protein),
                new BigDecimal(carbohydrate), new BigDecimal(fat),
                LocalDateTime.of(2026, 7, 1, 9, 0), LocalDateTime.of(2026, 7, 1, 9, 0)
        );
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

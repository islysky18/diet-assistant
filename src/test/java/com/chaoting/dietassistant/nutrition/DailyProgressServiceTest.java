package com.chaoting.dietassistant.nutrition;

import com.chaoting.dietassistant.food.DailyNutritionTotalsResponse;
import com.chaoting.dietassistant.food.FoodEntryService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DailyProgressServiceTest {

    private static final LocalDate DATE = LocalDate.of(2026, 7, 18);

    @Test
    void noGoalAndNoEntriesReturnsZeroConsumedWithoutGoalCalculations() {
        FoodEntryService foodEntryService = mock(FoodEntryService.class);
        NutritionGoalService goalService = mock(NutritionGoalService.class);
        when(foodEntryService.listEntriesForDate(DATE)).thenReturn(List.of());
        when(foodEntryService.calculateTotals(List.of())).thenReturn(DailyNutritionTotalsResponse.zero());
        when(goalService.getCurrentGoal()).thenReturn(Optional.empty());

        DailyProgressResponse response = new DailyProgressService(foodEntryService, goalService).getProgress(DATE);

        assertThat(response.selectedDate()).isEqualTo(DATE);
        assertThat(response.previousDate()).isEqualTo(DATE.minusDays(1));
        assertThat(response.nextDate()).isEqualTo(DATE.plusDays(1));
        assertThat(response.hasNutritionGoal()).isFalse();
        assertThat(response.foodEntries()).isEmpty();
        assertThat(response.calories().consumed()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.calories().goal()).isNull();
        assertThat(response.calories().remaining()).isNull();
        assertThat(response.calories().over()).isNull();
        assertThat(response.calories().percentage()).isNull();
    }

    @Test
    void calculatesRemainingAndPercentageWhenConsumptionIsBelowGoal() {
        DailyProgressService service = serviceWithTotalsAndGoal(
                totals("1500", "90", "180", "50"),
                goal("2000", "120", "250", "70")
        );

        DailyProgressResponse response = service.getProgress(DATE);

        assertThat(response.calories().remaining()).isEqualByComparingTo("500");
        assertThat(response.calories().over()).isEqualByComparingTo("0");
        assertThat(response.calories().percentage()).isEqualByComparingTo("75.00");
        assertThat(response.protein().remaining()).isEqualByComparingTo("30");
    }

    @Test
    void calculatesOverAndCapsRemainingAtZero() {
        DailyProgressService service = serviceWithTotalsAndGoal(
                totals("2300", "150", "280", "90"),
                goal("2000", "120", "250", "70")
        );

        DailyProgressResponse response = service.getProgress(DATE);

        assertThat(response.calories().remaining()).isEqualByComparingTo("0");
        assertThat(response.calories().over()).isEqualByComparingTo("300");
        assertThat(response.protein().over()).isEqualByComparingTo("30");
        assertThat(response.fat().over()).isEqualByComparingTo("20");
    }

    @Test
    void zeroGoalHasNoPercentageButStillCalculatesOver() {
        DailyProgressService service = serviceWithTotalsAndGoal(
                totals("100", "10", "20", "5"),
                goal("0", "0", "0", "0")
        );

        DailyProgressResponse response = service.getProgress(DATE);

        assertThat(response.calories().percentage()).isNull();
        assertThat(response.calories().remaining()).isEqualByComparingTo("0");
        assertThat(response.calories().over()).isEqualByComparingTo("100");
        assertThat(response.protein().percentage()).isNull();
    }

    private DailyProgressService serviceWithTotalsAndGoal(
            DailyNutritionTotalsResponse totals,
            NutritionGoalResponse goal
    ) {
        FoodEntryService foodEntryService = mock(FoodEntryService.class);
        NutritionGoalService goalService = mock(NutritionGoalService.class);
        when(foodEntryService.listEntriesForDate(DATE)).thenReturn(List.of());
        when(foodEntryService.calculateTotals(List.of())).thenReturn(totals);
        when(goalService.getCurrentGoal()).thenReturn(Optional.of(goal));
        return new DailyProgressService(foodEntryService, goalService);
    }

    private DailyNutritionTotalsResponse totals(String calories, String protein, String carbohydrate, String fat) {
        return new DailyNutritionTotalsResponse(
                new BigDecimal(calories),
                new BigDecimal(protein),
                new BigDecimal(carbohydrate),
                new BigDecimal(fat),
                BigDecimal.ZERO
        );
    }

    private NutritionGoalResponse goal(String calories, String protein, String carbohydrate, String fat) {
        return new NutritionGoalResponse(
                1L,
                7L,
                new BigDecimal(calories),
                new BigDecimal(protein),
                new BigDecimal(carbohydrate),
                new BigDecimal(fat),
                LocalDateTime.of(2026, 7, 1, 9, 0),
                LocalDateTime.of(2026, 7, 1, 9, 0)
        );
    }
}

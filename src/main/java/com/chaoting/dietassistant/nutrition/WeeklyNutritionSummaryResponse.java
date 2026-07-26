package com.chaoting.dietassistant.nutrition;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record WeeklyNutritionSummaryResponse(
        LocalDate weekStart,
        LocalDate weekEnd,
        LocalDate previousWeekStart,
        LocalDate nextWeekStart,
        LocalDate currentWeekStart,
        boolean currentWeek,
        String dateRangeLabel,
        int daysLogged,
        int foodEntryCount,
        BigDecimal weeklyCalories,
        BigDecimal averageCalories,
        List<WeeklyNutrientProgressResponse> nutrientProgress,
        List<DailyNutritionSummaryResponse> dailyBreakdown
) {
    public boolean empty() {
        return foodEntryCount == 0;
    }

    public LocalDate recordFoodDate(LocalDate today) {
        return currentWeek ? today : weekStart;
    }
}

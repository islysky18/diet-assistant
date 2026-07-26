package com.chaoting.dietassistant.nutrition;

import java.math.BigDecimal;

public record WeeklyNutrientProgressResponse(
        String name,
        BigDecimal dailyAverage,
        BigDecimal dailyGoal,
        BigDecimal percentage,
        String unit
) {
}

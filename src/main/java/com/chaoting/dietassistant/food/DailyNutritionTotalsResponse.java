package com.chaoting.dietassistant.food;

import java.math.BigDecimal;

public record DailyNutritionTotalsResponse(
        BigDecimal calories,
        BigDecimal proteinGrams,
        BigDecimal carbohydrateGrams,
        BigDecimal fatGrams,
        BigDecimal fiberGrams
) {
    public static DailyNutritionTotalsResponse zero() {
        return new DailyNutritionTotalsResponse(
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO
        );
    }
}

package com.chaoting.dietassistant.nutrition;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DailyNutritionSummaryResponse(
        LocalDate date,
        String dayName,
        BigDecimal calories,
        BigDecimal proteinGrams,
        BigDecimal carbohydrateGrams,
        BigDecimal fatGrams,
        boolean logged
) {
}

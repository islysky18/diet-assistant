package com.chaoting.dietassistant.nutrition;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record NutritionGoalResponse(
        Long id,
        Long profileId,
        BigDecimal dailyCalories,
        BigDecimal dailyProteinGrams,
        BigDecimal dailyCarbohydrateGrams,
        BigDecimal dailyFatGrams,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}

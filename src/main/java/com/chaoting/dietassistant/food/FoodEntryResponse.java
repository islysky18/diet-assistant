package com.chaoting.dietassistant.food;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record FoodEntryResponse(
        Long id,
        String foodName,
        BigDecimal amount,
        String unit,
        BigDecimal calories,
        BigDecimal proteinGrams,
        BigDecimal carbohydrateGrams,
        BigDecimal fatGrams,
        BigDecimal fiberGrams,
        MealType mealType,
        LocalDateTime eatenAt,
        String notes,
        LocalDateTime createdAt
) {
}

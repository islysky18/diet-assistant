package com.chaoting.dietassistant.food;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record FoodEntryResponse(
        Long id,
        Long savedFoodId,
        String savedFoodName,
        String savedFoodBrand,
        BigDecimal savedFoodReferenceAmount,
        String savedFoodReferenceUnit,
        BigDecimal savedFoodReferenceWeightGrams,
        String foodName,
        BigDecimal amount,
        String unit,
        BigDecimal calories,
        BigDecimal proteinGrams,
        BigDecimal carbohydrateGrams,
        BigDecimal fatGrams,
        BigDecimal fiberGrams,
        BigDecimal calculationMultiplier,
        MealType mealType,
        LocalDateTime eatenAt,
        String notes,
        LocalDateTime createdAt
) {
}

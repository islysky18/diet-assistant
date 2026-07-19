package com.chaoting.dietassistant.food;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record SavedFoodResponse(
        Long id,
        String name,
        String brand,
        BigDecimal referenceAmount,
        String referenceUnit,
        BigDecimal referenceWeightGrams,
        BigDecimal calories,
        BigDecimal proteinGrams,
        BigDecimal carbohydrateGrams,
        BigDecimal fatGrams,
        BigDecimal fiberGrams,
        String notes,
        boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public String getDisplayName() {
        if (brand == null || brand.isBlank()) {
            return name;
        }
        return brand + " - " + name;
    }

    public String getReferenceServing() {
        return referenceAmount + " " + referenceUnit;
    }
}

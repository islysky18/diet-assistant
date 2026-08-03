package com.chaoting.dietassistant.food;

import java.math.BigDecimal;

record PendingFoodImportResult(
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
        String notes
) {
}

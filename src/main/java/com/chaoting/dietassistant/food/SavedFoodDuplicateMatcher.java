package com.chaoting.dietassistant.food;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Locale;

@Component
class SavedFoodDuplicateMatcher {

    boolean matches(SavedFoodRequest requested, SavedFoodResponse existing) {
        String referenceUnit = FoodUnit.canonicalize(requested.getReferenceUnit());
        BigDecimal referenceWeight = FoodUnit.isWeight(referenceUnit) ? null : requested.getReferenceWeightGrams();
        return text(requested.getBrand()).equals(text(existing.brand()))
                && text(requested.getName()).equals(text(existing.name()))
                && number(requested.getReferenceAmount(), existing.referenceAmount())
                && text(referenceUnit).equals(text(existing.referenceUnit()))
                && number(referenceWeight, existing.referenceWeightGrams())
                && number(requested.getCalories(), existing.calories())
                && number(requested.getProteinGrams(), existing.proteinGrams())
                && number(requested.getCarbohydrateGrams(), existing.carbohydrateGrams())
                && number(requested.getFatGrams(), existing.fatGrams())
                && number(requested.getFiberGrams(), existing.fiberGrams());
    }

    private String text(String value) {
        if (value == null || value.isBlank()) return "";
        return value.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
    }

    private boolean number(BigDecimal left, BigDecimal right) {
        return left == null ? right == null : right != null && left.compareTo(right) == 0;
    }
}

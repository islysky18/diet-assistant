package com.chaoting.dietassistant.food;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Map;

final class FoodUnit {

    private static final Map<String, String> ALIASES = Map.ofEntries(
            Map.entry("g", "g"), Map.entry("gram", "g"), Map.entry("grams", "g"),
            Map.entry("kg", "kg"), Map.entry("kilogram", "kg"), Map.entry("kilograms", "kg"),
            Map.entry("oz", "oz"), Map.entry("ounce", "oz"), Map.entry("ounces", "oz"),
            Map.entry("lb", "lb"), Map.entry("lbs", "lb"), Map.entry("pound", "lb"), Map.entry("pounds", "lb"),
            Map.entry("ml", "ml"), Map.entry("milliliter", "ml"), Map.entry("milliliters", "ml"),
            Map.entry("l", "l"), Map.entry("liter", "l"), Map.entry("liters", "l"),
            Map.entry("tsp", "tsp"), Map.entry("teaspoon", "tsp"), Map.entry("teaspoons", "tsp"),
            Map.entry("tbsp", "tbsp"), Map.entry("tablespoon", "tbsp"), Map.entry("tablespoons", "tbsp"),
            Map.entry("cup", "cup"), Map.entry("cups", "cup"),
            Map.entry("serving", "serving"), Map.entry("servings", "serving"),
            Map.entry("piece", "piece"), Map.entry("pieces", "piece"),
            Map.entry("slice", "slice"), Map.entry("slices", "slice")
    );

    private static final Map<String, BigDecimal> GRAMS_PER_UNIT = Map.of(
            "g", BigDecimal.ONE,
            "kg", new BigDecimal("1000"),
            "oz", new BigDecimal("28.349523125"),
            "lb", new BigDecimal("453.59237")
    );

    private FoodUnit() {
    }

    static String canonicalize(String unit) {
        String trimmed = unit == null ? "" : unit.trim();
        return ALIASES.getOrDefault(trimmed.toLowerCase(Locale.ROOT), trimmed);
    }

    static boolean equivalent(String first, String second) {
        return canonicalize(first).equalsIgnoreCase(canonicalize(second));
    }

    static boolean isWeight(String unit) {
        return GRAMS_PER_UNIT.containsKey(canonicalize(unit));
    }

    static BigDecimal toGrams(BigDecimal amount, String unit) {
        BigDecimal factor = GRAMS_PER_UNIT.get(canonicalize(unit));
        if (factor == null) {
            throw new IllegalArgumentException("Unsupported weight unit: " + unit);
        }
        return amount.multiply(factor);
    }
}

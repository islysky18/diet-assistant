package com.chaoting.dietassistant.food;

import java.math.BigDecimal;

public record UsdaFoodSearchResult(
        long fdcId,
        String description,
        String dataType,
        String brand,
        BigDecimal servingSize,
        String servingSizeUnit
) { }

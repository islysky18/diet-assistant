package com.chaoting.dietassistant.nutrition;

import java.math.BigDecimal;

public record NutrientProgressResponse(
        BigDecimal consumed,
        BigDecimal goal,
        BigDecimal remaining,
        BigDecimal over,
        BigDecimal percentage
) {
}

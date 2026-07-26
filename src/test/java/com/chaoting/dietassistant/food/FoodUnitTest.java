package com.chaoting.dietassistant.food;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class FoodUnitTest {

    @Test
    void canonicalizesCommonAliases() {
        assertThat(FoodUnit.canonicalize(" Grams ")).isEqualTo("g");
        assertThat(FoodUnit.canonicalize("ounces")).isEqualTo("oz");
        assertThat(FoodUnit.canonicalize("lbs")).isEqualTo("lb");
        assertThat(FoodUnit.canonicalize("cups")).isEqualTo("cup");
        assertThat(FoodUnit.canonicalize("custom scoop")).isEqualTo("custom scoop");
    }

    @Test
    void convertsSupportedWeightUnitsToGrams() {
        assertThat(FoodUnit.toGrams(new BigDecimal("1"), "kg")).isEqualByComparingTo("1000");
        assertThat(FoodUnit.toGrams(new BigDecimal("2"), "oz")).isEqualByComparingTo("56.699046250");
        assertThat(FoodUnit.toGrams(new BigDecimal("1"), "pound")).isEqualByComparingTo("453.59237");
    }
}

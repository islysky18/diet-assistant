package com.chaoting.dietassistant.nutrition;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public class NutritionGoalRequest {

    @NotNull
    @DecimalMin("0")
    @Digits(integer = 8, fraction = 2)
    private BigDecimal dailyCalories;

    @NotNull
    @DecimalMin("0")
    @Digits(integer = 8, fraction = 2)
    private BigDecimal dailyProteinGrams;

    @NotNull
    @DecimalMin("0")
    @Digits(integer = 8, fraction = 2)
    private BigDecimal dailyCarbohydrateGrams;

    @NotNull
    @DecimalMin("0")
    @Digits(integer = 8, fraction = 2)
    private BigDecimal dailyFatGrams;

    public BigDecimal getDailyCalories() {
        return dailyCalories;
    }

    public void setDailyCalories(BigDecimal dailyCalories) {
        this.dailyCalories = dailyCalories;
    }

    public BigDecimal getDailyProteinGrams() {
        return dailyProteinGrams;
    }

    public void setDailyProteinGrams(BigDecimal dailyProteinGrams) {
        this.dailyProteinGrams = dailyProteinGrams;
    }

    public BigDecimal getDailyCarbohydrateGrams() {
        return dailyCarbohydrateGrams;
    }

    public void setDailyCarbohydrateGrams(BigDecimal dailyCarbohydrateGrams) {
        this.dailyCarbohydrateGrams = dailyCarbohydrateGrams;
    }

    public BigDecimal getDailyFatGrams() {
        return dailyFatGrams;
    }

    public void setDailyFatGrams(BigDecimal dailyFatGrams) {
        this.dailyFatGrams = dailyFatGrams;
    }
}

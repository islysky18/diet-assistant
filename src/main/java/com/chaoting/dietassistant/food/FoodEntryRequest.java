package com.chaoting.dietassistant.food;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class FoodEntryRequest {

    @NotBlank
    @Size(max = 255)
    private String foodName;

    @NotNull
    @Positive
    private BigDecimal amount;

    @NotBlank
    @Size(max = 50)
    private String unit;

    @NotNull
    @PositiveOrZero
    private BigDecimal calories;

    @NotNull
    @PositiveOrZero
    private BigDecimal proteinGrams;

    @NotNull
    @PositiveOrZero
    private BigDecimal carbohydrateGrams;

    @NotNull
    @PositiveOrZero
    private BigDecimal fatGrams;

    @NotNull
    @PositiveOrZero
    private BigDecimal fiberGrams;

    @NotNull
    private MealType mealType;

    @NotNull
    @PastOrPresent
    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime eatenAt;

    @Size(max = 2000)
    private String notes;

    public String getFoodName() {
        return foodName;
    }

    public void setFoodName(String foodName) {
        this.foodName = foodName;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public BigDecimal getCalories() {
        return calories;
    }

    public void setCalories(BigDecimal calories) {
        this.calories = calories;
    }

    public BigDecimal getProteinGrams() {
        return proteinGrams;
    }

    public void setProteinGrams(BigDecimal proteinGrams) {
        this.proteinGrams = proteinGrams;
    }

    public BigDecimal getCarbohydrateGrams() {
        return carbohydrateGrams;
    }

    public void setCarbohydrateGrams(BigDecimal carbohydrateGrams) {
        this.carbohydrateGrams = carbohydrateGrams;
    }

    public BigDecimal getFatGrams() {
        return fatGrams;
    }

    public void setFatGrams(BigDecimal fatGrams) {
        this.fatGrams = fatGrams;
    }

    public BigDecimal getFiberGrams() {
        return fiberGrams;
    }

    public void setFiberGrams(BigDecimal fiberGrams) {
        this.fiberGrams = fiberGrams;
    }

    public MealType getMealType() {
        return mealType;
    }

    public void setMealType(MealType mealType) {
        this.mealType = mealType;
    }

    public LocalDateTime getEatenAt() {
        return eatenAt;
    }

    public void setEatenAt(LocalDateTime eatenAt) {
        this.eatenAt = eatenAt;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}

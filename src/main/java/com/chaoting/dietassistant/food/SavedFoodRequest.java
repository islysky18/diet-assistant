package com.chaoting.dietassistant.food;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public class SavedFoodRequest {

    @NotBlank
    @Size(max = 255)
    private String name;

    @Size(max = 255)
    private String brand;

    @NotNull
    @Positive
    private BigDecimal referenceAmount;

    @NotBlank
    @Size(max = 50)
    private String referenceUnit;

    @Positive
    private BigDecimal referenceWeightGrams;

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

    @Size(max = 2000)
    private String notes;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getBrand() {
        return brand;
    }

    public void setBrand(String brand) {
        this.brand = brand;
    }

    public BigDecimal getReferenceAmount() {
        return referenceAmount;
    }

    public void setReferenceAmount(BigDecimal referenceAmount) {
        this.referenceAmount = referenceAmount;
    }

    public String getReferenceUnit() {
        return referenceUnit;
    }

    public void setReferenceUnit(String referenceUnit) {
        this.referenceUnit = referenceUnit;
    }

    public BigDecimal getReferenceWeightGrams() {
        return referenceWeightGrams;
    }

    public void setReferenceWeightGrams(BigDecimal referenceWeightGrams) {
        this.referenceWeightGrams = referenceWeightGrams;
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

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}

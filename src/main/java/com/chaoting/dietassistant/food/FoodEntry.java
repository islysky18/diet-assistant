package com.chaoting.dietassistant.food;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "food_entries")
class FoodEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "profile_id", nullable = false)
    private Long profileId;

    @Column(name = "saved_food_id")
    private Long savedFoodId;

    @Column(name = "saved_food_name", length = 255)
    private String savedFoodName;

    @Column(name = "saved_food_brand", length = 255)
    private String savedFoodBrand;

    @Column(name = "saved_food_reference_amount", precision = 10, scale = 2)
    private BigDecimal savedFoodReferenceAmount;

    @Column(name = "saved_food_reference_unit", length = 50)
    private String savedFoodReferenceUnit;

    @Column(name = "saved_food_reference_weight_grams", precision = 10, scale = 2)
    private BigDecimal savedFoodReferenceWeightGrams;

    @Column(name = "food_name", nullable = false, length = 255)
    private String foodName;

    @Column(name = "amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(name = "unit", nullable = false, length = 50)
    private String unit;

    @Column(name = "calories", nullable = false, precision = 10, scale = 2)
    private BigDecimal calories;

    @Column(name = "protein_grams", nullable = false, precision = 10, scale = 2)
    private BigDecimal proteinGrams;

    @Column(name = "carbohydrate_grams", nullable = false, precision = 10, scale = 2)
    private BigDecimal carbohydrateGrams;

    @Column(name = "fat_grams", nullable = false, precision = 10, scale = 2)
    private BigDecimal fatGrams;

    @Column(name = "fiber_grams", nullable = false, precision = 10, scale = 2)
    private BigDecimal fiberGrams;

    @Column(name = "calculation_multiplier", precision = 18, scale = 8)
    private BigDecimal calculationMultiplier;

    @Enumerated(EnumType.STRING)
    @Column(name = "meal_type", nullable = false, length = 50)
    private MealType mealType;

    @Column(name = "eaten_at", nullable = false)
    private LocalDateTime eatenAt;

    @Column(name = "notes", length = 2000)
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    Long getId() {
        return id;
    }

    Long getProfileId() {
        return profileId;
    }

    void setProfileId(Long profileId) {
        this.profileId = profileId;
    }

    Long getSavedFoodId() {
        return savedFoodId;
    }

    void setSavedFoodId(Long savedFoodId) {
        this.savedFoodId = savedFoodId;
    }

    String getSavedFoodName() {
        return savedFoodName;
    }

    void setSavedFoodName(String savedFoodName) {
        this.savedFoodName = savedFoodName;
    }

    String getSavedFoodBrand() {
        return savedFoodBrand;
    }

    void setSavedFoodBrand(String savedFoodBrand) {
        this.savedFoodBrand = savedFoodBrand;
    }

    BigDecimal getSavedFoodReferenceAmount() {
        return savedFoodReferenceAmount;
    }

    void setSavedFoodReferenceAmount(BigDecimal savedFoodReferenceAmount) {
        this.savedFoodReferenceAmount = savedFoodReferenceAmount;
    }

    String getSavedFoodReferenceUnit() {
        return savedFoodReferenceUnit;
    }

    void setSavedFoodReferenceUnit(String savedFoodReferenceUnit) {
        this.savedFoodReferenceUnit = savedFoodReferenceUnit;
    }

    BigDecimal getSavedFoodReferenceWeightGrams() {
        return savedFoodReferenceWeightGrams;
    }

    void setSavedFoodReferenceWeightGrams(BigDecimal savedFoodReferenceWeightGrams) {
        this.savedFoodReferenceWeightGrams = savedFoodReferenceWeightGrams;
    }

    String getFoodName() {
        return foodName;
    }

    void setFoodName(String foodName) {
        this.foodName = foodName;
    }

    BigDecimal getAmount() {
        return amount;
    }

    void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    String getUnit() {
        return unit;
    }

    void setUnit(String unit) {
        this.unit = unit;
    }

    BigDecimal getCalories() {
        return calories;
    }

    void setCalories(BigDecimal calories) {
        this.calories = calories;
    }

    BigDecimal getProteinGrams() {
        return proteinGrams;
    }

    void setProteinGrams(BigDecimal proteinGrams) {
        this.proteinGrams = proteinGrams;
    }

    BigDecimal getCarbohydrateGrams() {
        return carbohydrateGrams;
    }

    void setCarbohydrateGrams(BigDecimal carbohydrateGrams) {
        this.carbohydrateGrams = carbohydrateGrams;
    }

    BigDecimal getFatGrams() {
        return fatGrams;
    }

    void setFatGrams(BigDecimal fatGrams) {
        this.fatGrams = fatGrams;
    }

    BigDecimal getFiberGrams() {
        return fiberGrams;
    }

    void setFiberGrams(BigDecimal fiberGrams) {
        this.fiberGrams = fiberGrams;
    }

    BigDecimal getCalculationMultiplier() {
        return calculationMultiplier;
    }

    void setCalculationMultiplier(BigDecimal calculationMultiplier) {
        this.calculationMultiplier = calculationMultiplier;
    }

    MealType getMealType() {
        return mealType;
    }

    void setMealType(MealType mealType) {
        this.mealType = mealType;
    }

    LocalDateTime getEatenAt() {
        return eatenAt;
    }

    void setEatenAt(LocalDateTime eatenAt) {
        this.eatenAt = eatenAt;
    }

    String getNotes() {
        return notes;
    }

    void setNotes(String notes) {
        this.notes = notes;
    }

    LocalDateTime getCreatedAt() {
        return createdAt;
    }

    void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}

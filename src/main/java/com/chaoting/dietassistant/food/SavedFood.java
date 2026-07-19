package com.chaoting.dietassistant.food;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "saved_foods")
class SavedFood {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "profile_id", nullable = false)
    private Long profileId;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "brand", length = 255)
    private String brand;

    @Column(name = "reference_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal referenceAmount;

    @Column(name = "reference_unit", nullable = false, length = 50)
    private String referenceUnit;

    @Column(name = "reference_weight_grams", precision = 10, scale = 2)
    private BigDecimal referenceWeightGrams;

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

    @Column(name = "notes", length = 2000)
    private String notes;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    Long getId() {
        return id;
    }

    Long getProfileId() {
        return profileId;
    }

    void setProfileId(Long profileId) {
        this.profileId = profileId;
    }

    String getName() {
        return name;
    }

    void setName(String name) {
        this.name = name;
    }

    String getBrand() {
        return brand;
    }

    void setBrand(String brand) {
        this.brand = brand;
    }

    BigDecimal getReferenceAmount() {
        return referenceAmount;
    }

    void setReferenceAmount(BigDecimal referenceAmount) {
        this.referenceAmount = referenceAmount;
    }

    String getReferenceUnit() {
        return referenceUnit;
    }

    void setReferenceUnit(String referenceUnit) {
        this.referenceUnit = referenceUnit;
    }

    BigDecimal getReferenceWeightGrams() {
        return referenceWeightGrams;
    }

    void setReferenceWeightGrams(BigDecimal referenceWeightGrams) {
        this.referenceWeightGrams = referenceWeightGrams;
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

    String getNotes() {
        return notes;
    }

    void setNotes(String notes) {
        this.notes = notes;
    }

    boolean isActive() {
        return active;
    }

    void setActive(boolean active) {
        this.active = active;
    }

    LocalDateTime getCreatedAt() {
        return createdAt;
    }

    void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}

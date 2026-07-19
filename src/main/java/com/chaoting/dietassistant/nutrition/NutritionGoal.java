package com.chaoting.dietassistant.nutrition;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "nutrition_goals")
class NutritionGoal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "profile_id", nullable = false, unique = true)
    private Long profileId;

    @Column(name = "daily_calories", nullable = false, precision = 10, scale = 2)
    private BigDecimal dailyCalories;

    @Column(name = "daily_protein_grams", nullable = false, precision = 10, scale = 2)
    private BigDecimal dailyProteinGrams;

    @Column(name = "daily_carbohydrate_grams", nullable = false, precision = 10, scale = 2)
    private BigDecimal dailyCarbohydrateGrams;

    @Column(name = "daily_fat_grams", nullable = false, precision = 10, scale = 2)
    private BigDecimal dailyFatGrams;

    @Column(name = "created_at", nullable = false)
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

    BigDecimal getDailyCalories() {
        return dailyCalories;
    }

    void setDailyCalories(BigDecimal dailyCalories) {
        this.dailyCalories = dailyCalories;
    }

    BigDecimal getDailyProteinGrams() {
        return dailyProteinGrams;
    }

    void setDailyProteinGrams(BigDecimal dailyProteinGrams) {
        this.dailyProteinGrams = dailyProteinGrams;
    }

    BigDecimal getDailyCarbohydrateGrams() {
        return dailyCarbohydrateGrams;
    }

    void setDailyCarbohydrateGrams(BigDecimal dailyCarbohydrateGrams) {
        this.dailyCarbohydrateGrams = dailyCarbohydrateGrams;
    }

    BigDecimal getDailyFatGrams() {
        return dailyFatGrams;
    }

    void setDailyFatGrams(BigDecimal dailyFatGrams) {
        this.dailyFatGrams = dailyFatGrams;
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

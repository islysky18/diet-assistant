package com.chaoting.dietassistant.profile;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalTime;

@Entity
@Table(name = "profiles")
class Profile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "singleton_key", nullable = false, unique = true)
    private Integer singletonKey = 1;

    @Column(name = "birth_year")
    private Integer birthYear;

    @Column(name = "height_cm", precision = 5, scale = 2)
    private BigDecimal heightCm;

    @Column(name = "weight_kg", precision = 5, scale = 2)
    private BigDecimal weightKg;

    @Enumerated(EnumType.STRING)
    @Column(name = "primary_goal", length = 50)
    private PrimaryGoal primaryGoal;

    @Column(name = "meals_per_day")
    private Integer mealsPerDay;

    @Column(name = "weekday_dinner_time")
    private LocalTime weekdayDinnerTime;

    @Column(name = "notes", length = 2000)
    private String notes;

    Long getId() {
        return id;
    }

    Integer getBirthYear() {
        return birthYear;
    }

    void setBirthYear(Integer birthYear) {
        this.birthYear = birthYear;
    }

    BigDecimal getHeightCm() {
        return heightCm;
    }

    void setHeightCm(BigDecimal heightCm) {
        this.heightCm = heightCm;
    }

    BigDecimal getWeightKg() {
        return weightKg;
    }

    void setWeightKg(BigDecimal weightKg) {
        this.weightKg = weightKg;
    }

    PrimaryGoal getPrimaryGoal() {
        return primaryGoal;
    }

    void setPrimaryGoal(PrimaryGoal primaryGoal) {
        this.primaryGoal = primaryGoal;
    }

    Integer getMealsPerDay() {
        return mealsPerDay;
    }

    void setMealsPerDay(Integer mealsPerDay) {
        this.mealsPerDay = mealsPerDay;
    }

    LocalTime getWeekdayDinnerTime() {
        return weekdayDinnerTime;
    }

    void setWeekdayDinnerTime(LocalTime weekdayDinnerTime) {
        this.weekdayDinnerTime = weekdayDinnerTime;
    }

    String getNotes() {
        return notes;
    }

    void setNotes(String notes) {
        this.notes = notes;
    }
}

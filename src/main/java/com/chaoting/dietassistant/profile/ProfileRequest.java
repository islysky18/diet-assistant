package com.chaoting.dietassistant.profile;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalTime;

public class ProfileRequest {

    @Min(1900)
    @Max(2100)
    private Integer birthYear;

    @DecimalMin("50.00")
    @DecimalMax("300.00")
    private BigDecimal heightCm;

    @DecimalMin("20.00")
    @DecimalMax("500.00")
    private BigDecimal weightKg;

    private PrimaryGoal primaryGoal;

    @Min(1)
    @Max(10)
    private Integer mealsPerDay;

    @DateTimeFormat(pattern = "HH:mm")
    private LocalTime weekdayDinnerTime;

    @Size(max = 2000)
    private String notes;

    public Integer getBirthYear() {
        return birthYear;
    }

    public void setBirthYear(Integer birthYear) {
        this.birthYear = birthYear;
    }

    public BigDecimal getHeightCm() {
        return heightCm;
    }

    public void setHeightCm(BigDecimal heightCm) {
        this.heightCm = heightCm;
    }

    public BigDecimal getWeightKg() {
        return weightKg;
    }

    public void setWeightKg(BigDecimal weightKg) {
        this.weightKg = weightKg;
    }

    public PrimaryGoal getPrimaryGoal() {
        return primaryGoal;
    }

    public void setPrimaryGoal(PrimaryGoal primaryGoal) {
        this.primaryGoal = primaryGoal;
    }

    public Integer getMealsPerDay() {
        return mealsPerDay;
    }

    public void setMealsPerDay(Integer mealsPerDay) {
        this.mealsPerDay = mealsPerDay;
    }

    public LocalTime getWeekdayDinnerTime() {
        return weekdayDinnerTime;
    }

    public void setWeekdayDinnerTime(LocalTime weekdayDinnerTime) {
        this.weekdayDinnerTime = weekdayDinnerTime;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}

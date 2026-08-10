package com.chaoting.dietassistant.energy;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;

public class EnergyFields {
    @DecimalMin("0.00") @DecimalMax("20000.00") private BigDecimal activeEnergyKcal;
    @DecimalMin("0.00") @DecimalMax("20000.00") private BigDecimal restingEnergyKcal;
    @Min(0) @Max(500000) private Integer steps;
    @Min(0) @Max(1440) private Integer exerciseMinutes;
    @NotBlank private String timezone;

    public BigDecimal getActiveEnergyKcal() { return activeEnergyKcal; }
    public void setActiveEnergyKcal(BigDecimal value) { activeEnergyKcal = value; }
    public BigDecimal getRestingEnergyKcal() { return restingEnergyKcal; }
    public void setRestingEnergyKcal(BigDecimal value) { restingEnergyKcal = value; }
    public Integer getSteps() { return steps; }
    public void setSteps(Integer value) { steps = value; }
    public Integer getExerciseMinutes() { return exerciseMinutes; }
    public void setExerciseMinutes(Integer value) { exerciseMinutes = value; }
    public String getTimezone() { return timezone; }
    public void setTimezone(String value) { timezone = value; }
}

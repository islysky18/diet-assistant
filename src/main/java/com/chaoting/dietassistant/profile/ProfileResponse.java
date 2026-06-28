package com.chaoting.dietassistant.profile;

import java.math.BigDecimal;
import java.time.LocalTime;

public record ProfileResponse(
        Long id,
        Integer birthYear,
        BigDecimal heightCm,
        BigDecimal weightKg,
        PrimaryGoal primaryGoal,
        Integer mealsPerDay,
        LocalTime weekdayDinnerTime,
        String notes
) {
}

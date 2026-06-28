package com.chaoting.dietassistant.health;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record HealthMetricResponse(
        Long id,
        MetricType metricType,
        BigDecimal value,
        String unit,
        LocalDate measuredDate,
        String notes,
        LocalDateTime createdAt
) {
}

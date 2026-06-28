package com.chaoting.dietassistant.health;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "health_metrics")
class HealthMetric {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "profile_id", nullable = false)
    private Long profileId;

    @Enumerated(EnumType.STRING)
    @Column(name = "metric_type", nullable = false, length = 50)
    private MetricType metricType;

    @Column(name = "value", nullable = false, precision = 10, scale = 2)
    private BigDecimal value;

    @Column(name = "unit", nullable = false, length = 50)
    private String unit;

    @Column(name = "measured_date", nullable = false)
    private LocalDate measuredDate;

    @Column(name = "notes", length = 2000)
    private String notes;

    @Column(name = "created_at", nullable = false)
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

    MetricType getMetricType() {
        return metricType;
    }

    void setMetricType(MetricType metricType) {
        this.metricType = metricType;
    }

    BigDecimal getValue() {
        return value;
    }

    void setValue(BigDecimal value) {
        this.value = value;
    }

    String getUnit() {
        return unit;
    }

    void setUnit(String unit) {
        this.unit = unit;
    }

    LocalDate getMeasuredDate() {
        return measuredDate;
    }

    void setMeasuredDate(LocalDate measuredDate) {
        this.measuredDate = measuredDate;
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

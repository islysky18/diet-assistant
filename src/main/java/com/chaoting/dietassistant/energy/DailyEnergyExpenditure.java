package com.chaoting.dietassistant.energy;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "daily_energy_expenditures")
class DailyEnergyExpenditure {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "profile_id", nullable = false) private Long profileId;
    @Column(name = "activity_date", nullable = false) private LocalDate activityDate;
    @Column(name = "active_energy_kcal", precision = 10, scale = 2) private BigDecimal activeEnergyKcal;
    @Column(name = "resting_energy_kcal", precision = 10, scale = 2) private BigDecimal restingEnergyKcal;
    private Integer steps;
    @Column(name = "exercise_minutes") private Integer exerciseMinutes;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private EnergySource source;
    @Column(nullable = false, length = 100) private String timezone;
    @Column(name = "source_updated_at") private LocalDateTime sourceUpdatedAt;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;

    Long getId() { return id; }
    Long getProfileId() { return profileId; }
    void setProfileId(Long value) { profileId = value; }
    LocalDate getActivityDate() { return activityDate; }
    void setActivityDate(LocalDate value) { activityDate = value; }
    BigDecimal getActiveEnergyKcal() { return activeEnergyKcal; }
    void setActiveEnergyKcal(BigDecimal value) { activeEnergyKcal = value; }
    BigDecimal getRestingEnergyKcal() { return restingEnergyKcal; }
    void setRestingEnergyKcal(BigDecimal value) { restingEnergyKcal = value; }
    Integer getSteps() { return steps; }
    void setSteps(Integer value) { steps = value; }
    Integer getExerciseMinutes() { return exerciseMinutes; }
    void setExerciseMinutes(Integer value) { exerciseMinutes = value; }
    EnergySource getSource() { return source; }
    void setSource(EnergySource value) { source = value; }
    String getTimezone() { return timezone; }
    void setTimezone(String value) { timezone = value; }
    LocalDateTime getSourceUpdatedAt() { return sourceUpdatedAt; }
    void setSourceUpdatedAt(LocalDateTime value) { sourceUpdatedAt = value; }
    LocalDateTime getCreatedAt() { return createdAt; }
    void setCreatedAt(LocalDateTime value) { createdAt = value; }
    LocalDateTime getUpdatedAt() { return updatedAt; }
    void setUpdatedAt(LocalDateTime value) { updatedAt = value; }
}

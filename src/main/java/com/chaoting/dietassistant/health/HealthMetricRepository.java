package com.chaoting.dietassistant.health;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface HealthMetricRepository extends JpaRepository<HealthMetric, Long> {

    List<HealthMetric> findByProfileIdOrderByMeasuredDateDescIdDesc(Long profileId);
}

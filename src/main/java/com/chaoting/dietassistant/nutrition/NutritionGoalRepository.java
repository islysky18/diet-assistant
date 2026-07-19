package com.chaoting.dietassistant.nutrition;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

interface NutritionGoalRepository extends JpaRepository<NutritionGoal, Long> {

    Optional<NutritionGoal> findByProfileId(Long profileId);
}

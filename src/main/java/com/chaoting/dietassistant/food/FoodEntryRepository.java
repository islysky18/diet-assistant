package com.chaoting.dietassistant.food;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

interface FoodEntryRepository extends JpaRepository<FoodEntry, Long> {

    List<FoodEntry> findByProfileIdAndEatenAtGreaterThanEqualAndEatenAtLessThanOrderByEatenAtDescIdDesc(
            Long profileId,
            LocalDateTime startInclusive,
            LocalDateTime endExclusive
    );

    Optional<FoodEntry> findByIdAndProfileId(Long id, Long profileId);
}

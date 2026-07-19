package com.chaoting.dietassistant.food;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

interface SavedFoodRepository extends JpaRepository<SavedFood, Long> {

    List<SavedFood> findByProfileIdAndActiveTrueOrderByNameAscBrandAscIdAsc(Long profileId);

    Optional<SavedFood> findByIdAndProfileId(Long id, Long profileId);

    Optional<SavedFood> findByIdAndProfileIdAndActiveTrue(Long id, Long profileId);
}

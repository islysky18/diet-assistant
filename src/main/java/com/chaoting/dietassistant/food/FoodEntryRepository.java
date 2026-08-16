package com.chaoting.dietassistant.food;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    @Query("""
            select entry
            from FoodEntry entry
            where entry.profileId = :profileId
              and entry.savedFoodId is not null
              and exists (
                  select savedFood.id
                  from SavedFood savedFood
                  where savedFood.id = entry.savedFoodId
                    and savedFood.profileId = :profileId
                    and savedFood.active = true
              )
              and not exists (
                  select newer.id
                  from FoodEntry newer
                  where newer.profileId = entry.profileId
                    and newer.savedFoodId = entry.savedFoodId
                    and (newer.eatenAt > entry.eatenAt
                         or (newer.eatenAt = entry.eatenAt and newer.id > entry.id))
              )
            order by entry.eatenAt desc, entry.id desc
            """)
    List<FoodEntry> findRecentUniqueSavedFoodEntries(
            @Param("profileId") Long profileId,
            Pageable pageable
    );
}

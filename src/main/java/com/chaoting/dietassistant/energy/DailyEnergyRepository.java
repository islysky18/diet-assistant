package com.chaoting.dietassistant.energy;

import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

interface DailyEnergyRepository extends JpaRepository<DailyEnergyExpenditure, Long> {
    Optional<DailyEnergyExpenditure> findByProfileIdAndActivityDateAndSource(Long profileId, LocalDate date, EnergySource source);
    List<DailyEnergyExpenditure> findByProfileIdAndActivityDate(Long profileId, LocalDate date);
}

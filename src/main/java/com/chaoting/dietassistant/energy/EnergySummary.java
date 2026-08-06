package com.chaoting.dietassistant.energy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZonedDateTime;

public record EnergySummary(LocalDate date, BigDecimal caloriesConsumed,
        BigDecimal activeEnergyKcal, BigDecimal restingEnergyKcal, BigDecimal totalBurned,
        boolean partialData, String balanceLabel, BigDecimal balanceAmount,
        BigDecimal projectedTotalBurn, String projectedBalanceLabel, BigDecimal projectedBalanceAmount,
        Integer steps, Integer exerciseMinutes, String sourceLabel, ZonedDateTime lastUpdated,
        boolean hasEnergyData, boolean today) { }

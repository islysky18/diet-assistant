package com.chaoting.dietassistant.energy;

import com.chaoting.dietassistant.food.DailyNutritionTotalsResponse;
import com.chaoting.dietassistant.food.FoodEntryService;
import com.chaoting.dietassistant.profile.CurrentProfileProvider;
import com.chaoting.dietassistant.profile.ProfileResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DailyEnergyServiceTest {
    private static final LocalDate DATE = LocalDate.of(2026, 8, 5);
    private DailyEnergyRepository repository;
    private FoodEntryService foods;
    private DailyEnergyService service;

    @BeforeEach void setUp() {
        repository = mock(DailyEnergyRepository.class);
        CurrentProfileProvider profiles = mock(CurrentProfileProvider.class);
        foods = mock(FoodEntryService.class);
        when(profiles.getProfile()).thenReturn(Optional.of(new ProfileResponse(7L, null, null, null, null, null, null, null)));
        when(foods.totalsForDate(any())).thenReturn(new DailyNutritionTotalsResponse(new BigDecimal("1200"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
        Clock clock = Clock.fixed(Instant.parse("2026-08-06T03:00:00Z"), ZoneId.of("America/Los_Angeles"));
        service = new DailyEnergyService(repository, profiles, foods, clock);
    }

    @Test void appleValuesWinWhileNullsFallBackToManualWithoutAddingSources() {
        DailyEnergyExpenditure manual = row(EnergySource.MANUAL, "400", "1000", 5000);
        DailyEnergyExpenditure apple = row(EnergySource.APPLE_HEALTH, "0", null, 8000);
        when(repository.findByProfileIdAndActivityDate(7L, DATE)).thenReturn(List.of(manual, apple));

        EnergySummary summary = service.summary(DATE);

        assertThat(summary.activeEnergyKcal()).isEqualByComparingTo("0");
        assertThat(summary.restingEnergyKcal()).isEqualByComparingTo("1000");
        assertThat(summary.totalBurned()).isEqualByComparingTo("1000");
        assertThat(summary.balanceLabel()).isEqualTo("Surplus");
        assertThat(summary.balanceAmount()).isEqualByComparingTo("200");
        assertThat(summary.sourceLabel()).startsWith("Mixed");
    }

    @Test void projectsOnlyRestingEnergyAfterOneHour() {
        when(repository.findByProfileIdAndActivityDate(7L, DATE))
                .thenReturn(List.of(row(EnergySource.APPLE_HEALTH, "500", "1000", null)));
        EnergySummary summary = service.summary(DATE);
        assertThat(summary.projectedTotalBurn()).isEqualByComparingTo("1700.00");
        assertThat(summary.projectedBalanceLabel()).isEqualTo("Deficit");
        assertThat(summary.projectedBalanceAmount()).isEqualByComparingTo("500.00");
    }

    @Test void historicalAndFirstHourProjectionAreUnavailable() {
        DailyEnergyExpenditure row = row(EnergySource.MANUAL, null, "100", null);
        when(repository.findByProfileIdAndActivityDate(7L, DATE.minusDays(1))).thenReturn(List.of(row));
        assertThat(service.summary(DATE.minusDays(1)).projectedTotalBurn()).isNull();

        Clock early = Clock.fixed(Instant.parse("2026-08-05T07:30:00Z"), ZoneId.of("America/Los_Angeles"));
        DailyEnergyService earlyService = new DailyEnergyService(repository, mockProfile(), foods, early);
        when(repository.findByProfileIdAndActivityDate(7L, DATE)).thenReturn(List.of(row));
        assertThat(earlyService.summary(DATE).projectedTotalBurn()).isNull();
    }

    @Test void projectionUsesTimezoneAcrossDstTransition() {
        Clock dstClock = Clock.fixed(Instant.parse("2026-03-08T19:00:00Z"), ZoneId.of("America/Los_Angeles"));
        DailyEnergyService dstService = new DailyEnergyService(repository, mockProfile(), foods, dstClock);
        LocalDate dstDate = LocalDate.of(2026, 3, 8);
        DailyEnergyExpenditure row = row(EnergySource.APPLE_HEALTH, "300", "550", null);
        row.setActivityDate(dstDate);
        when(repository.findByProfileIdAndActivityDate(7L, dstDate)).thenReturn(List.of(row));

        assertThat(dstService.summary(dstDate).projectedTotalBurn()).isEqualByComparingTo("1500.00");
    }

    @Test void staleAndMissingTimestampCannotOverwriteTimestampedAppleData() {
        DailyEnergyExpenditure existing = row(EnergySource.APPLE_HEALTH, "400", "1000", 10);
        existing.setSourceUpdatedAt(LocalDateTime.of(2026, 8, 6, 2, 0));
        when(repository.findByProfileIdAndActivityDateAndSource(7L, DATE, EnergySource.APPLE_HEALTH)).thenReturn(Optional.of(existing));
        AppleHealthEnergyRequest stale = request("America/Los_Angeles");
        stale.setSourceUpdatedAt(OffsetDateTime.parse("2026-08-05T18:00:00-07:00"));
        assertThatThrownBy(() -> service.syncAppleHealth(DATE, stale)).isInstanceOf(StaleEnergyUpdateException.class);
        assertThatThrownBy(() -> service.syncAppleHealth(DATE, request("America/Los_Angeles"))).isInstanceOf(StaleEnergyUpdateException.class);
        verify(repository, never()).save(any());
    }

    @Test void manualSaveRejectsFutureDateAndInvalidTimezone() {
        ManualEnergyRequest request = new ManualEnergyRequest();
        request.setDate(DATE.plusDays(1)); request.setTimezone("America/Los_Angeles");
        assertThatIllegalArgumentException().isThrownBy(() -> service.saveManual(request)).withMessageContaining("future");
        request.setDate(DATE); request.setTimezone("Not/AZone");
        assertThatIllegalArgumentException().isThrownBy(() -> service.saveManual(request)).withMessageContaining("IANA");
    }

    private CurrentProfileProvider mockProfile() {
        CurrentProfileProvider value = mock(CurrentProfileProvider.class);
        when(value.getProfile()).thenReturn(Optional.of(new ProfileResponse(7L, null, null, null, null, null, null, null)));
        return value;
    }
    private AppleHealthEnergyRequest request(String timezone) { AppleHealthEnergyRequest r = new AppleHealthEnergyRequest(); r.setTimezone(timezone); return r; }
    private DailyEnergyExpenditure row(EnergySource source, String active, String resting, Integer steps) {
        DailyEnergyExpenditure row = new DailyEnergyExpenditure(); row.setProfileId(7L); row.setActivityDate(DATE); row.setSource(source);
        row.setActiveEnergyKcal(active == null ? null : new BigDecimal(active)); row.setRestingEnergyKcal(resting == null ? null : new BigDecimal(resting));
        row.setSteps(steps); row.setTimezone("America/Los_Angeles"); row.setCreatedAt(LocalDateTime.of(2026,8,6,2,0)); row.setUpdatedAt(LocalDateTime.of(2026,8,6,2,0)); return row;
    }
}

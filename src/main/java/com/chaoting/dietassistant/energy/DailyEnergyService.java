package com.chaoting.dietassistant.energy;

import com.chaoting.dietassistant.food.FoodEntryService;
import com.chaoting.dietassistant.profile.CurrentProfileProvider;
import com.chaoting.dietassistant.profile.ProfileResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

@Service
public class DailyEnergyService {
    private static final BigDecimal HOURS_PER_DAY = new BigDecimal("24");
    private final DailyEnergyRepository repository;
    private final CurrentProfileProvider profiles;
    private final FoodEntryService foodEntries;
    private final Clock clock;

    public DailyEnergyService(DailyEnergyRepository repository, CurrentProfileProvider profiles,
                              FoodEntryService foodEntries, Clock clock) {
        this.repository = repository;
        this.profiles = profiles;
        this.foodEntries = foodEntries;
        this.clock = clock;
    }

    public ManualEnergyRequest newManualRequest(LocalDate date) {
        LocalDate selected = date == null ? LocalDate.now(clock) : date;
        ManualEnergyRequest request = new ManualEnergyRequest();
        request.setDate(selected);
        request.setTimezone(clock.getZone().getId());
        profiles.getProfile().flatMap(p -> repository.findByProfileIdAndActivityDateAndSource(p.id(), selected, EnergySource.MANUAL))
                .ifPresent(row -> copyToRequest(row, request));
        return request;
    }

    @Transactional
    public void saveManual(ManualEnergyRequest request) {
        validateDateAndTimezone(request.getDate(), request.getTimezone());
        ProfileResponse profile = requireProfile();
        DailyEnergyExpenditure row = repository.findByProfileIdAndActivityDateAndSource(
                profile.id(), request.getDate(), EnergySource.MANUAL).orElseGet(DailyEnergyExpenditure::new);
        boolean created = row.getId() == null;
        if (created) initialize(row, profile.id(), request.getDate(), EnergySource.MANUAL);
        apply(row, request);
        row.setSourceUpdatedAt(null);
        touch(row, created);
        repository.save(row);
    }

    @Transactional
    public EnergySyncResponse syncAppleHealth(LocalDate date, AppleHealthEnergyRequest request) {
        validateDateAndTimezone(date, request.getTimezone());
        ProfileResponse profile = requireProfile();
        Optional<DailyEnergyExpenditure> existing = repository.findByProfileIdAndActivityDateAndSource(
                profile.id(), date, EnergySource.APPLE_HEALTH);
        LocalDateTime incoming = request.getSourceUpdatedAt() == null ? null
                : LocalDateTime.ofInstant(request.getSourceUpdatedAt().toInstant(), ZoneOffset.UTC);
        if (existing.isPresent() && existing.get().getSourceUpdatedAt() != null
                && (incoming == null || incoming.isBefore(existing.get().getSourceUpdatedAt()))) {
            throw new StaleEnergyUpdateException();
        }
        DailyEnergyExpenditure row = existing.orElseGet(DailyEnergyExpenditure::new);
        boolean created = row.getId() == null;
        if (created) initialize(row, profile.id(), date, EnergySource.APPLE_HEALTH);
        apply(row, request);
        row.setSourceUpdatedAt(incoming);
        touch(row, created);
        repository.save(row);
        return new EnergySyncResponse(date, EnergySource.APPLE_HEALTH, true, request.getSourceUpdatedAt());
    }

    @Transactional(readOnly = true)
    public EnergySummary summary(LocalDate date) {
        BigDecimal consumed = foodEntries.totalsForDate(date).calories();
        List<DailyEnergyExpenditure> rows = profiles.getProfile()
                .map(p -> repository.findByProfileIdAndActivityDate(p.id(), date)).orElseGet(List::of);
        DailyEnergyExpenditure apple = find(rows, EnergySource.APPLE_HEALTH);
        DailyEnergyExpenditure manual = find(rows, EnergySource.MANUAL);
        BigDecimal active = preferred(apple, manual, DailyEnergyExpenditure::getActiveEnergyKcal);
        BigDecimal resting = preferred(apple, manual, DailyEnergyExpenditure::getRestingEnergyKcal);
        Integer steps = preferred(apple, manual, DailyEnergyExpenditure::getSteps);
        Integer exercise = preferred(apple, manual, DailyEnergyExpenditure::getExerciseMinutes);
        BigDecimal total = sumKnown(active, resting);
        Balance current = balance(consumed, total);
        boolean today = date.equals(LocalDate.now(clock));
        BigDecimal projected = projectedTotal(date, active, resting, timezone(apple, manual));
        Balance projectedBalance = balance(consumed, projected);
        DailyEnergyExpenditure latest = rows.stream().max(Comparator.comparing(DailyEnergyExpenditure::getUpdatedAt)).orElse(null);
        return new EnergySummary(date, consumed, active, resting, total,
                total != null && (active == null || resting == null), current.label(), current.amount(),
                projected, projectedBalance.label(), projectedBalance.amount(), steps, exercise,
                sourceLabel(apple, manual), displayUpdated(latest), !rows.isEmpty(), today);
    }

    private BigDecimal projectedTotal(LocalDate date, BigDecimal active, BigDecimal resting, String timezone) {
        if (resting == null || !date.equals(LocalDate.now(clock)) || timezone == null) return null;
        ZoneId zone = zone(timezone);
        ZonedDateTime now = clock.instant().atZone(zone);
        if (!now.toLocalDate().equals(date)) return null;
        BigDecimal elapsed = BigDecimal.valueOf(Duration.between(date.atStartOfDay(zone), now).toMillis())
                .divide(BigDecimal.valueOf(3_600_000), 8, RoundingMode.HALF_UP);
        if (elapsed.compareTo(BigDecimal.ONE) < 0) return null;
        BigDecimal projectedResting = resting.multiply(HOURS_PER_DAY).divide(elapsed, 8, RoundingMode.HALF_UP).max(resting);
        return (active == null ? BigDecimal.ZERO : active).add(projectedResting).setScale(2, RoundingMode.HALF_UP);
    }

    private void validateDateAndTimezone(LocalDate date, String timezone) {
        if (date == null || date.isAfter(LocalDate.now(clock))) throw new IllegalArgumentException("Activity date cannot be in the future.");
        zone(timezone);
    }
    private ZoneId zone(String value) {
        try { return ZoneId.of(value); } catch (RuntimeException ex) { throw new IllegalArgumentException("Enter a valid IANA timezone."); }
    }
    private ProfileResponse requireProfile() {
        return profiles.getProfile().orElseThrow(() -> new IllegalStateException("Create the user profile before recording energy data."));
    }
    private void initialize(DailyEnergyExpenditure row, Long profileId, LocalDate date, EnergySource source) {
        row.setProfileId(profileId); row.setActivityDate(date); row.setSource(source);
    }
    private void apply(DailyEnergyExpenditure row, EnergyFields request) {
        row.setActiveEnergyKcal(request.getActiveEnergyKcal()); row.setRestingEnergyKcal(request.getRestingEnergyKcal());
        row.setSteps(request.getSteps()); row.setExerciseMinutes(request.getExerciseMinutes()); row.setTimezone(request.getTimezone().trim());
    }
    private void touch(DailyEnergyExpenditure row, boolean created) {
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        if (created) row.setCreatedAt(now); row.setUpdatedAt(now);
    }
    private void copyToRequest(DailyEnergyExpenditure row, ManualEnergyRequest request) {
        request.setActiveEnergyKcal(row.getActiveEnergyKcal()); request.setRestingEnergyKcal(row.getRestingEnergyKcal());
        request.setSteps(row.getSteps()); request.setExerciseMinutes(row.getExerciseMinutes()); request.setTimezone(row.getTimezone());
    }
    private DailyEnergyExpenditure find(List<DailyEnergyExpenditure> rows, EnergySource source) {
        return rows.stream().filter(r -> r.getSource() == source).findFirst().orElse(null);
    }
    private <T> T preferred(DailyEnergyExpenditure apple, DailyEnergyExpenditure manual, Function<DailyEnergyExpenditure,T> field) {
        T appleValue = apple == null ? null : field.apply(apple);
        return appleValue != null ? appleValue : manual == null ? null : field.apply(manual);
    }
    private BigDecimal sumKnown(BigDecimal one, BigDecimal two) {
        if (one == null && two == null) return null;
        return (one == null ? BigDecimal.ZERO : one).add(two == null ? BigDecimal.ZERO : two);
    }
    private Balance balance(BigDecimal consumed, BigDecimal burned) {
        if (burned == null) return new Balance(null, null);
        BigDecimal value = consumed.subtract(burned);
        return value.signum() < 0 ? new Balance("Deficit", value.abs())
                : value.signum() > 0 ? new Balance("Surplus", value) : new Balance("Balanced", BigDecimal.ZERO);
    }
    private String sourceLabel(DailyEnergyExpenditure apple, DailyEnergyExpenditure manual) {
        if (apple == null && manual == null) return "Not available";
        if (apple == null) return "Manual";
        if (manual == null) return "Apple Health via iPhone Shortcut";
        boolean fallback = (apple.getActiveEnergyKcal() == null && manual.getActiveEnergyKcal() != null)
                || (apple.getRestingEnergyKcal() == null && manual.getRestingEnergyKcal() != null)
                || (apple.getSteps() == null && manual.getSteps() != null)
                || (apple.getExerciseMinutes() == null && manual.getExerciseMinutes() != null);
        return fallback ? "Mixed (Apple Health with manual fallback)" : "Apple Health via iPhone Shortcut";
    }
    private String timezone(DailyEnergyExpenditure apple, DailyEnergyExpenditure manual) {
        return apple != null ? apple.getTimezone() : manual != null ? manual.getTimezone() : null;
    }
    private ZonedDateTime displayUpdated(DailyEnergyExpenditure row) {
        return row == null ? null : row.getUpdatedAt().atZone(ZoneOffset.UTC).withZoneSameInstant(zone(row.getTimezone()));
    }
    private record Balance(String label, BigDecimal amount) { }
}

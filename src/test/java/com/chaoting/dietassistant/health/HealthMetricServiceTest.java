package com.chaoting.dietassistant.health;

import com.chaoting.dietassistant.profile.ProfileResponse;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HealthMetricServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-06-22T10:15:30Z"),
            ZoneOffset.UTC
    );

    @Test
    void metricTypesProvideExpectedDefaultUnits() {
        assertThat(MetricType.HBA1C.getDefaultUnit()).isEqualTo("%");
        assertThat(MetricType.LDL.getDefaultUnit()).isEqualTo("mg/dL");
        assertThat(MetricType.HDL.getDefaultUnit()).isEqualTo("mg/dL");
        assertThat(MetricType.TRIGLYCERIDES.getDefaultUnit()).isEqualTo("mg/dL");
        assertThat(MetricType.TOTAL_CHOLESTEROL.getDefaultUnit()).isEqualTo("mg/dL");
        assertThat(MetricType.FASTING_GLUCOSE.getDefaultUnit()).isEqualTo("mg/dL");
        assertThat(MetricType.SYSTOLIC_BLOOD_PRESSURE.getDefaultUnit()).isEqualTo("mmHg");
        assertThat(MetricType.DIASTOLIC_BLOOD_PRESSURE.getDefaultUnit()).isEqualTo("mmHg");
        assertThat(MetricType.WAIST_CIRCUMFERENCE.getDefaultUnit()).isEqualTo("cm");
        assertThat(MetricType.WEIGHT.getDefaultUnit()).isEqualTo("kg");
    }

    @Test
    void createStoresMetricForCurrentProfileAndTrimsText() {
        FakeHealthMetricRepository healthMetricRepository = new FakeHealthMetricRepository();
        HealthMetricService healthMetricService = new HealthMetricService(
                healthMetricRepository.proxy(),
                () -> Optional.of(profile()),
                FIXED_CLOCK
        );

        HealthMetricResponse response = healthMetricService.create(request(
                MetricType.HBA1C,
                new BigDecimal("5.70"),
                " % ",
                LocalDate.of(2026, 6, 20),
                "  Annual lab  "
        ));

        assertThat(response.metricType()).isEqualTo(MetricType.HBA1C);
        assertThat(response.value()).isEqualByComparingTo("5.70");
        assertThat(response.unit()).isEqualTo("%");
        assertThat(response.measuredDate()).isEqualTo(LocalDate.of(2026, 6, 20));
        assertThat(response.notes()).isEqualTo("Annual lab");
        assertThat(response.createdAt()).isEqualTo(LocalDateTime.of(2026, 6, 22, 10, 15, 30));
    }

    @Test
    void listMetricsReturnsMetricsOrderedByRepositoryResult() {
        FakeHealthMetricRepository healthMetricRepository = new FakeHealthMetricRepository();
        HealthMetricService healthMetricService = new HealthMetricService(
                healthMetricRepository.proxy(),
                () -> Optional.of(profile()),
                FIXED_CLOCK
        );
        HealthMetric first = metric(MetricType.WEIGHT, "80.50", "kg", LocalDate.of(2026, 6, 22));
        HealthMetric second = metric(MetricType.LDL, "100.00", "mg/dL", LocalDate.of(2026, 5, 1));
        healthMetricRepository.metrics = List.of(first, second);

        List<HealthMetricResponse> responses = healthMetricService.listMetrics();

        assertThat(responses).extracting(HealthMetricResponse::metricType)
                .containsExactly(MetricType.WEIGHT, MetricType.LDL);
    }

    @Test
    void listMetricsReturnsEmptyListWhenProfileDoesNotExist() {
        FakeHealthMetricRepository healthMetricRepository = new FakeHealthMetricRepository();
        HealthMetricService healthMetricService = new HealthMetricService(
                healthMetricRepository.proxy(),
                Optional::empty,
                FIXED_CLOCK
        );

        List<HealthMetricResponse> responses = healthMetricService.listMetrics();

        assertThat(responses).isEmpty();
    }

    @Test
    void deleteIgnoresMetricForAnotherProfile() {
        FakeHealthMetricRepository healthMetricRepository = new FakeHealthMetricRepository();
        HealthMetricService healthMetricService = new HealthMetricService(
                healthMetricRepository.proxy(),
                () -> Optional.of(profile()),
                FIXED_CLOCK
        );
        HealthMetric metric = metric(MetricType.WEIGHT, "80.50", "kg", LocalDate.of(2026, 6, 22));
        metric.setProfileId(99L);
        healthMetricRepository.metricById = Optional.of(metric);

        healthMetricService.delete(10L);

        assertThat(healthMetricRepository.deletedMetric).isNull();
    }

    @Test
    void createRequiresProfile() {
        FakeHealthMetricRepository healthMetricRepository = new FakeHealthMetricRepository();
        HealthMetricService healthMetricService = new HealthMetricService(
                healthMetricRepository.proxy(),
                Optional::empty,
                FIXED_CLOCK
        );

        assertThatThrownBy(() -> healthMetricService.create(request(
                MetricType.WEIGHT,
                new BigDecimal("80.50"),
                "kg",
                LocalDate.of(2026, 6, 22),
                null
        ))).isInstanceOf(IllegalStateException.class)
                .hasMessage("Create the user profile before recording health metrics.");
    }

    private HealthMetricRequest request(
            MetricType metricType,
            BigDecimal value,
            String unit,
            LocalDate measuredDate,
            String notes
    ) {
        HealthMetricRequest request = new HealthMetricRequest();
        request.setMetricType(metricType);
        request.setValue(value);
        request.setUnit(unit);
        request.setMeasuredDate(measuredDate);
        request.setNotes(notes);
        return request;
    }

    private ProfileResponse profile() {
        return new ProfileResponse(
                7L,
                1988,
                new BigDecimal("175.50"),
                new BigDecimal("72.25"),
                null,
                3,
                LocalTime.of(18, 30),
                null
        );
    }

    private HealthMetric metric(MetricType metricType, String value, String unit, LocalDate measuredDate) {
        HealthMetric healthMetric = new HealthMetric();
        healthMetric.setProfileId(7L);
        healthMetric.setMetricType(metricType);
        healthMetric.setValue(new BigDecimal(value));
        healthMetric.setUnit(unit);
        healthMetric.setMeasuredDate(measuredDate);
        healthMetric.setCreatedAt(LocalDateTime.of(2026, 6, 22, 10, 15, 30));
        return healthMetric;
    }

    private static class FakeHealthMetricRepository {

        private List<HealthMetric> metrics = List.of();
        private Optional<HealthMetric> metricById = Optional.empty();
        private HealthMetric deletedMetric;

        HealthMetricRepository proxy() {
            return (HealthMetricRepository) Proxy.newProxyInstance(
                    HealthMetricRepository.class.getClassLoader(),
                    new Class<?>[]{HealthMetricRepository.class},
                    (proxy, method, args) -> {
                        if (method.getName().equals("save")) {
                            return args[0];
                        }
                        if (method.getName().equals("findByProfileIdOrderByMeasuredDateDescIdDesc")) {
                            return metrics;
                        }
                        if (method.getName().equals("findById")) {
                            return metricById;
                        }
                        if (method.getName().equals("delete")) {
                            deletedMetric = (HealthMetric) args[0];
                            return null;
                        }
                        if (method.getName().equals("toString")) {
                            return "FakeHealthMetricRepository";
                        }
                        throw new UnsupportedOperationException(method.getName());
                    }
            );
        }
    }
}

package com.chaoting.dietassistant.health;

import com.chaoting.dietassistant.profile.ProfileRequest;
import com.chaoting.dietassistant.profile.ProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.CookieManager;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class HealthMetricIntegrationTest {

    @Container
    @ServiceConnection
    static final MySQLContainer<?> mysql = new MySQLContainer<>(DockerImageName.parse("mysql:8.4"));

    @LocalServerPort
    private int port;

    @Autowired
    private HealthMetricRepository healthMetricRepository;

    @Autowired
    private ProfileService profileService;

    private HttpClient httpClient;

    @BeforeEach
    void setUp() {
        healthMetricRepository.deleteAll();
        createProfile();
        httpClient = HttpClient.newBuilder()
                .cookieHandler(new CookieManager())
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Test
    void getHealthShowsFormAndSavedMetricsOrderedByMeasuredDateDescending() throws IOException, InterruptedException {
        saveMetric(MetricType.LDL, "102.00", "mg/dL", LocalDate.of(2026, 5, 1), "Earlier lab");
        saveMetric(MetricType.WEIGHT, "80.50", "kg", LocalDate.of(2026, 6, 1), "Recent weigh-in");

        HttpResponse<String> response = get("/health");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("Health", "Save health metric", "LDL", "Total cholesterol", "Weight");
        assertThat(response.body()).contains(
                "value=\"TOTAL_CHOLESTEROL\"",
                "data-default-unit=\"mg/dL\"",
                "value=\"SYSTOLIC_BLOOD_PRESSURE\"",
                "data-default-unit=\"mmHg\"",
                "value=\"WEIGHT\"",
                "data-default-unit=\"kg\""
        );
        assertThat(response.body()).containsSubsequence("2026-06-01", "2026-05-01");
    }

    @Test
    void postHealthCreatesMetricThenDeleteRemovesIt() throws IOException, InterruptedException {
        HttpResponse<String> createResponse = post("/health", Map.of(
                "metricType", "FASTING_GLUCOSE",
                "value", "94.00",
                "unit", "mg/dL",
                "measuredDate", "2026-06-20",
                "notes", "Morning"
        ));

        assertThat(createResponse.statusCode()).isEqualTo(200);
        assertThat(createResponse.uri().getPath()).startsWith("/health");
        assertThat(createResponse.body()).contains("Health metric saved.");
        assertThat(healthMetricRepository.count()).isEqualTo(1);

        HealthMetric savedMetric = healthMetricRepository.findAll().getFirst();
        assertThat(savedMetric.getMetricType()).isEqualTo(MetricType.FASTING_GLUCOSE);
        assertThat(savedMetric.getValue()).isEqualByComparingTo("94.00");
        assertThat(savedMetric.getUnit()).isEqualTo("mg/dL");
        assertThat(savedMetric.getMeasuredDate()).isEqualTo(LocalDate.of(2026, 6, 20));
        assertThat(savedMetric.getNotes()).isEqualTo("Morning");

        HttpResponse<String> deleteResponse = post("/health/" + savedMetric.getId() + "/delete", Map.of());

        assertThat(deleteResponse.statusCode()).isEqualTo(200);
        assertThat(deleteResponse.uri().getPath()).startsWith("/health");
        assertThat(deleteResponse.body()).contains("Health metric deleted.");
        assertThat(healthMetricRepository.count()).isZero();
    }

    @Test
    void postHealthReturnsFormWhenValidationFails() throws IOException, InterruptedException {
        HttpResponse<String> response = post("/health", Map.of(
                "metricType", "WEIGHT",
                "value", "0",
                "unit", "",
                "measuredDate", "2999-01-01"
        ));

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("must be greater than 0");
        assertThat(response.body()).contains("must not be blank");
        assertThat(response.body()).contains("must be a date in the past or in the present");
        assertThat(healthMetricRepository.count()).isZero();
    }

    private void createProfile() {
        ProfileRequest request = new ProfileRequest();
        request.setBirthYear(1988);
        request.setHeightCm(new BigDecimal("175.50"));
        request.setWeightKg(new BigDecimal("72.25"));
        profileService.save(request);
    }

    private void saveMetric(
            MetricType metricType,
            String value,
            String unit,
            LocalDate measuredDate,
            String notes
    ) {
        HealthMetricRequest request = new HealthMetricRequest();
        request.setMetricType(metricType);
        request.setValue(new BigDecimal(value));
        request.setUnit(unit);
        request.setMeasuredDate(measuredDate);
        request.setNotes(notes);
        new HealthMetricService(healthMetricRepository, profileService, java.time.Clock.systemDefaultZone())
                .create(request);
    }

    private HttpResponse<String> get(String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri(path)).GET().build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, Map<String, String> formValues)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri(path))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(formBody(formValues)))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private String formBody(Map<String, String> formValues) {
        return formValues.entrySet().stream()
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .collect(Collectors.joining("&"));
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}

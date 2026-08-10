package com.chaoting.dietassistant.energy;

import com.chaoting.dietassistant.profile.ProfileRequest;
import com.chaoting.dietassistant.profile.ProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.net.URI;
import java.net.http.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "diet-assistant.local-sync-token=test-only-token")
@Testcontainers
class DailyEnergyIntegrationTest {
    @Container @ServiceConnection static final MySQLContainer<?> mysql = new MySQLContainer<>(DockerImageName.parse("mysql:8.4"));
    @LocalServerPort int port;
    @Autowired DailyEnergyRepository repository;
    @Autowired ProfileService profiles;
    @Autowired JdbcTemplate jdbc;
    HttpClient client;
    LocalDate date;

    @BeforeEach void setUp() {
        repository.deleteAll();
        if (profiles.getProfile().isEmpty()) profiles.save(new ProfileRequest());
        client = HttpClient.newHttpClient();
        date = LocalDate.now().minusDays(1);
    }

    @Test void apiRequiresTokenAndUpsertsWhileRejectingStalePayload() throws Exception {
        assertThat(put(json("2026-08-05T20:00:00-07:00"), null).statusCode()).isEqualTo(401);
        HttpResponse<String> created = put(json("2026-08-05T20:00:00-07:00"), "test-only-token");
        assertThat(created.statusCode()).isEqualTo(200);
        assertThat(created.body()).contains("APPLE_HEALTH", date.toString()).doesNotContain("profileId", "\"id\"");
        assertThat(repository.count()).isEqualTo(1);

        HttpResponse<String> stale = put(json("2026-08-05T19:00:00-07:00"), "test-only-token");
        assertThat(stale.statusCode()).isEqualTo(409);
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test void apiPreservesExplicitZeroAndNullableFields() throws Exception {
        String body = "{\"activeEnergyKcal\":0,\"restingEnergyKcal\":null,\"steps\":0,\"timezone\":\"America/Los_Angeles\"}";
        assertThat(put(body, "test-only-token").statusCode()).isEqualTo(200);
        DailyEnergyExpenditure row = repository.findAll().getFirst();
        assertThat(row.getActiveEnergyKcal()).isEqualByComparingTo("0");
        assertThat(row.getRestingEnergyKcal()).isNull();
        assertThat(row.getSteps()).isZero();
    }

    @Test void webRoutesAreUnprotectedAndManualFormDoesNotCopyAppleValues() throws Exception {
        put(json("2026-08-05T20:00:00-07:00"), "test-only-token");
        HttpResponse<String> response = client.send(HttpRequest.newBuilder(uri("/daily-energy?date=" + date)).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains(
                        "Daily energy",
                        "Enter cumulative totals for this date. Saving replaces the existing manual values; it does not add to them.",
                        "Manual values are fallback values",
                        "they are not added to Apple Health values",
                        "value=\"" + date + "\"")
                .doesNotContain("value=\"540.30\"");
    }

    @Test void manualFormPrefillsAndSecondSubmissionReplacesRatherThanAccumulates() throws Exception {
        assertThat(postManual("120.25", "900.50", "4000", "30").statusCode()).isEqualTo(302);
        assertThat(repository.count()).isEqualTo(1);

        HttpResponse<String> form = client.send(
                HttpRequest.newBuilder(uri("/daily-energy?date=" + date)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(form.statusCode()).isEqualTo(200);
        assertThat(form.body()).contains(
                "value=\"120.25\"", "value=\"900.50\"", "value=\"4000\"", "value=\"30\"",
                "Saving replaces the existing manual values; it does not add to them.");

        assertThat(postManual("200.00", "1000.00", "6500", "45").statusCode()).isEqualTo(302);
        assertThat(repository.count()).isEqualTo(1);
        DailyEnergyExpenditure updated = repository.findAll().getFirst();
        assertThat(updated.getActiveEnergyKcal()).isEqualByComparingTo("200.00");
        assertThat(updated.getRestingEnergyKcal()).isEqualByComparingTo("1000.00");
        assertThat(updated.getSteps()).isEqualTo(6500);
        assertThat(updated.getExerciseMinutes()).isEqualTo(45);

        HttpResponse<String> today = client.send(
                HttpRequest.newBuilder(uri("/?date=" + date)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(today.body()).contains("Edit daily activity totals").doesNotContain("Enter activity totals");
    }

    @Test void todayDashboardRendersWhenNoEnergyRowExists() throws Exception {
        HttpResponse<String> response = client.send(
                HttpRequest.newBuilder(uri("/")).GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains(
                "Energy balance", "No activity data is available", "Not available", "Enter activity totals");
    }

    @Test void databaseEnforcesUniqueSourceAndCascadesProfileDeletion() throws Exception {
        assertThat(put("{\"activeEnergyKcal\":0,\"timezone\":\"America/Los_Angeles\"}", "test-only-token").statusCode()).isEqualTo(200);
        Long profileId = profiles.getProfile().orElseThrow().id();
        assertThat(repository.findByProfileIdAndActivityDate(profileId, date)).hasSize(1);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO daily_energy_expenditures " +
                "(profile_id, activity_date, source, timezone, created_at, updated_at) VALUES (?, ?, 'APPLE_HEALTH', 'UTC', NOW(), NOW())",
                profileId, date)).isInstanceOf(RuntimeException.class);
        jdbc.update("DELETE FROM profiles WHERE id = ?", profileId);
        assertThat(repository.count()).isZero();
    }

    private String json(String timestamp) {
        return "{\"activeEnergyKcal\":540.30,\"restingEnergyKcal\":1260.80,\"steps\":8421,\"exerciseMinutes\":47," +
                "\"timezone\":\"America/Los_Angeles\",\"sourceUpdatedAt\":\"" + timestamp + "\"}";
    }
    private HttpResponse<String> put(String body, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri("/api/health/daily-energy/" + date))
                .header("Content-Type", "application/json").PUT(HttpRequest.BodyPublishers.ofString(body));
        if (token != null) builder.header("Authorization", "Bearer " + token);
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
    private HttpResponse<String> postManual(String active, String resting, String steps, String exercise) throws Exception {
        Map<String, String> values = Map.of(
                "date", date.toString(), "activeEnergyKcal", active, "restingEnergyKcal", resting,
                "steps", steps, "exerciseMinutes", exercise, "timezone", "America/Los_Angeles");
        String body = values.entrySet().stream()
                .map(entry -> URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8) + "="
                        + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
        HttpRequest request = HttpRequest.newBuilder(uri("/daily-energy"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
    private URI uri(String path) { return URI.create("http://localhost:" + port + path); }
}

package com.chaoting.dietassistant.profile;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.net.CookieManager;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class ProfileIntegrationTest {

    @Container
    @ServiceConnection
    static final MySQLContainer<?> mysql = new MySQLContainer<>(DockerImageName.parse("mysql:8.4"));

    @LocalServerPort
    private int port;

    @Autowired
    private ProfileRepository profileRepository;

    private HttpClient httpClient;

    @BeforeEach
    void setUp() {
        profileRepository.deleteAll();
        httpClient = HttpClient.newBuilder()
                .cookieHandler(new CookieManager())
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Test
    void getProfileShowsEmptyFormWhenProfileDoesNotExist() throws IOException, InterruptedException {
        HttpResponse<String> response = get("/profile");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("Profile", "Save profile");
    }

    @Test
    void postProfileCreatesThenUpdatesSingleProfile() throws IOException, InterruptedException {
        HttpResponse<String> createResponse = post("/profile", Map.of(
                "birthYear", "1988",
                "heightCm", "175.50",
                "weightKg", "72.25",
                "primaryGoal", "MAINTAIN_WEIGHT",
                "mealsPerDay", "3",
                "weekdayDinnerTime", "18:30",
                "notes", "Initial profile"
        ));

        assertThat(createResponse.statusCode()).isEqualTo(200);
        assertThat(createResponse.uri().getPath()).startsWith("/profile");
        assertThat(createResponse.body()).contains("Profile saved.");

        assertThat(profileRepository.count()).isEqualTo(1);
        Profile created = profileRepository.findFirstByOrderByIdAsc().orElseThrow();
        assertThat(created.getBirthYear()).isEqualTo(1988);
        assertThat(created.getHeightCm()).isEqualByComparingTo("175.50");
        assertThat(created.getWeightKg()).isEqualByComparingTo("72.25");
        assertThat(created.getPrimaryGoal()).isEqualTo(PrimaryGoal.MAINTAIN_WEIGHT);
        assertThat(created.getWeekdayDinnerTime()).isEqualTo(LocalTime.of(18, 30));

        HttpResponse<String> updateResponse = post("/profile", Map.of(
                "birthYear", "1990",
                "heightCm", "176.00",
                "weightKg", "70.00",
                "primaryGoal", "GAIN_MUSCLE",
                "mealsPerDay", "4",
                "weekdayDinnerTime", "19:00",
                "notes", "Updated profile"
        ));

        assertThat(updateResponse.statusCode()).isEqualTo(200);
        assertThat(updateResponse.uri().getPath()).startsWith("/profile");
        assertThat(updateResponse.body()).contains("Profile saved.");

        assertThat(profileRepository.count()).isEqualTo(1);
        Profile updated = profileRepository.findFirstByOrderByIdAsc().orElseThrow();
        assertThat(updated.getId()).isEqualTo(created.getId());
        assertThat(updated.getBirthYear()).isEqualTo(1990);
        assertThat(updated.getHeightCm()).isEqualByComparingTo("176.00");
        assertThat(updated.getWeightKg()).isEqualByComparingTo("70.00");
        assertThat(updated.getPrimaryGoal()).isEqualTo(PrimaryGoal.GAIN_MUSCLE);
        assertThat(updated.getMealsPerDay()).isEqualTo(4);
        assertThat(updated.getWeekdayDinnerTime()).isEqualTo(LocalTime.of(19, 0));
        assertThat(updated.getNotes()).isEqualTo("Updated profile");
    }

    @Test
    void postProfileReturnsFormWhenValidationFails() throws IOException, InterruptedException {
        HttpResponse<String> response = post("/profile", Map.of(
                "birthYear", "1800",
                "heightCm", "20",
                "weightKg", "10",
                "mealsPerDay", "0"
        ));

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("must be greater than or equal to 1900");
        assertThat(response.body()).contains("must be greater than or equal to 50.00");
        assertThat(response.body()).contains("must be greater than or equal to 20.00");
        assertThat(response.body()).contains("must be greater than or equal to 1");
        assertThat(profileRepository.count()).isZero();
    }

    @Test
    void databaseRejectsSecondProfileRow() {
        profileRepository.saveAndFlush(new Profile());

        assertThatThrownBy(() -> profileRepository.saveAndFlush(new Profile()))
                .isInstanceOf(DataIntegrityViolationException.class);
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

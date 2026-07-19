package com.chaoting.dietassistant.nutrition;

import com.chaoting.dietassistant.profile.ProfileRequest;
import com.chaoting.dietassistant.profile.ProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
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
import java.time.LocalDateTime;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class NutritionGoalIntegrationTest {

    @Container
    @ServiceConnection
    static final MySQLContainer<?> mysql = new MySQLContainer<>(DockerImageName.parse("mysql:8.4"));

    @LocalServerPort
    private int port;

    @Autowired
    private NutritionGoalRepository nutritionGoalRepository;

    @Autowired
    private ProfileService profileService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private HttpClient httpClient;
    private Long profileId;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM food_entries");
        nutritionGoalRepository.deleteAll();
        jdbcTemplate.update("DELETE FROM profiles");
        profileId = createProfile();
        httpClient = HttpClient.newBuilder()
                .cookieHandler(new CookieManager())
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Test
    void getAndPostNutritionGoalsCreateThenUpdateCurrentGoal() throws IOException, InterruptedException {
        HttpResponse<String> getResponse = get("/nutrition-goals");

        assertThat(getResponse.statusCode()).isEqualTo(200);
        assertThat(getResponse.body()).contains(
                "Nutrition Goals",
                "Set nutrition goals",
                "user-selected tracking goals",
                "not medical advice"
        );

        HttpResponse<String> createResponse = post("/nutrition-goals", goalForm("2000", "120", "250", "70"));

        assertThat(createResponse.statusCode()).isEqualTo(200);
        assertThat(createResponse.uri().getPath()).startsWith("/nutrition-goals");
        assertThat(createResponse.body()).contains("Nutrition goals saved.", "Update nutrition goals");
        assertThat(nutritionGoalRepository.count()).isEqualTo(1);
        NutritionGoal created = nutritionGoalRepository.findAll().getFirst();
        assertThat(created.getProfileId()).isEqualTo(profileId);
        assertThat(created.getDailyCalories()).isEqualByComparingTo("2000.00");

        HttpResponse<String> updateResponse = post("/nutrition-goals", goalForm("2100", "130", "260", "75"));

        assertThat(updateResponse.statusCode()).isEqualTo(200);
        assertThat(nutritionGoalRepository.count()).isEqualTo(1);
        NutritionGoal updated = nutritionGoalRepository.findAll().getFirst();
        assertThat(updated.getId()).isEqualTo(created.getId());
        assertThat(updated.getDailyCalories()).isEqualByComparingTo("2100.00");
        assertThat(updated.getDailyProteinGrams()).isEqualByComparingTo("130.00");
    }

    @Test
    void postNutritionGoalsReturnsFormForValidationFailures() throws IOException, InterruptedException {
        HttpResponse<String> response = post("/nutrition-goals", goalForm("-1", "123456789", "1.001", ""));

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains(
                "must be greater than or equal to 0",
                "numeric value out of bounds",
                "must not be null"
        );
        assertThat(nutritionGoalRepository.count()).isZero();
    }

    @Test
    void databaseEnforcesNonNegativeValuesAndOneGoalPerProfile() {
        insertGoal(profileId, "2000", "120", "250", "70");

        assertThatThrownBy(() -> insertGoal(profileId, "2100", "130", "260", "75"))
                .isInstanceOf(DataIntegrityViolationException.class);

        nutritionGoalRepository.deleteAll();
        assertThatThrownBy(() -> insertGoal(profileId, "-0.01", "120", "250", "70"))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void deletingProfileCascadesToNutritionGoal() {
        insertGoal(profileId, "2000", "120", "250", "70");

        jdbcTemplate.update("DELETE FROM profiles WHERE id = ?", profileId);

        assertThat(nutritionGoalRepository.count()).isZero();
    }

    @Test
    void rootDefaultsToTodayAndHandlesNoGoalOrEntries() throws IOException, InterruptedException {
        LocalDate today = LocalDate.now();

        HttpResponse<String> response = get("/");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains(
                "Daily Progress",
                today.toString(),
                "Previous day",
                "Next day",
                "Set nutrition goals",
                "No food entries recorded for this date.",
                "Consumed:"
        );
    }

    @Test
    void rootSelectedDateUsesInclusiveStartAndExclusiveNextDay() throws IOException, InterruptedException {
        LocalDate selectedDate = LocalDate.of(2026, 7, 18);
        insertFoodEntry("Previous boundary", selectedDate.minusDays(1).atTime(23, 59, 59, 999_999_000), "50");
        insertFoodEntry("Inclusive start", selectedDate.atStartOfDay(), "100");
        insertFoodEntry("Inside selected date", selectedDate.atTime(23, 59, 59), "200");
        insertFoodEntry("Exclusive next day", selectedDate.plusDays(1).atStartOfDay(), "400");
        insertGoal(profileId, "1000", "100", "200", "60");

        HttpResponse<String> response = get("/?date=2026-07-18");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains(
                "2026-07-18",
                "Inclusive start",
                "Inside selected date",
                "300.00",
                "700.00",
                "30.00%"
        );
        assertThat(response.body()).doesNotContain("Previous boundary", "Exclusive next day");
    }

    private Long createProfile() {
        ProfileRequest request = new ProfileRequest();
        request.setBirthYear(1988);
        request.setHeightCm(new BigDecimal("175.50"));
        request.setWeightKg(new BigDecimal("72.25"));
        return profileService.save(request).id();
    }

    private void insertGoal(Long ownerProfileId, String calories, String protein, String carbohydrate, String fat) {
        jdbcTemplate.update("""
                INSERT INTO nutrition_goals (
                    profile_id,
                    daily_calories,
                    daily_protein_grams,
                    daily_carbohydrate_grams,
                    daily_fat_grams,
                    created_at,
                    updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                ownerProfileId,
                new BigDecimal(calories),
                new BigDecimal(protein),
                new BigDecimal(carbohydrate),
                new BigDecimal(fat),
                LocalDateTime.now(),
                LocalDateTime.now()
        );
    }

    private void insertFoodEntry(String foodName, LocalDateTime eatenAt, String calories) {
        jdbcTemplate.update("""
                INSERT INTO food_entries (
                    profile_id,
                    food_name,
                    amount,
                    unit,
                    calories,
                    protein_grams,
                    carbohydrate_grams,
                    fat_grams,
                    fiber_grams,
                    meal_type,
                    eaten_at,
                    created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                profileId,
                foodName,
                new BigDecimal("1.00"),
                "serving",
                new BigDecimal(calories),
                new BigDecimal("10.00"),
                new BigDecimal("20.00"),
                new BigDecimal("5.00"),
                new BigDecimal("2.00"),
                "SNACK",
                eatenAt,
                LocalDateTime.now()
        );
    }

    private Map<String, String> goalForm(String calories, String protein, String carbohydrate, String fat) {
        return Map.of(
                "dailyCalories", calories,
                "dailyProteinGrams", protein,
                "dailyCarbohydrateGrams", carbohydrate,
                "dailyFatGrams", fat
        );
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

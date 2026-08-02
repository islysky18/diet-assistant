package com.chaoting.dietassistant.nutrition;

import com.chaoting.dietassistant.profile.ProfileRequest;
import com.chaoting.dietassistant.profile.ProfileService;
import com.chaoting.dietassistant.food.FoodEntryResponse;
import com.chaoting.dietassistant.food.FoodEntryService;
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

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class WeeklyNutritionSummaryIntegrationTest {

    @Container
    @ServiceConnection
    static final MySQLContainer<?> mysql = new MySQLContainer<>(DockerImageName.parse("mysql:8.4"));

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ProfileService profileService;

    @Autowired
    private FoodEntryService foodEntryService;

    @Autowired
    private NutritionGoalService nutritionGoalService;

    private HttpClient httpClient;
    private Long profileId;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM food_entries");
        jdbcTemplate.update("DELETE FROM nutrition_goals");
        jdbcTemplate.update("DELETE FROM profiles");
        ProfileRequest request = new ProfileRequest();
        request.setBirthYear(1988);
        request.setHeightCm(new BigDecimal("175.50"));
        request.setWeightKg(new BigDecimal("72.25"));
        profileId = profileService.save(request).id();
        httpClient = HttpClient.newHttpClient();
    }

    @Test
    void emptyPastWeekRendersNavigationSevenDaysAndMondayRecordFoodLink() throws Exception {
        HttpResponse<String> response = get("/nutrition-summary?weekStart=2025-12-31");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains(
                "Weekly Nutrition Summary",
                "Dec 29, 2025–Jan 4, 2026",
                "Previous Week",
                "Next Week",
                "This Week",
                "0 of 7",
                "No food entries recorded for this week.",
                "Record food entries to see your weekly nutrition summary.",
                "/food?date=2025-12-29",
                "/food?date=2026-01-04",
                "No entries"
        );
        assertThat(count(response.body(), "<tr>")).isEqualTo(8);
    }

    @Test
    void invalidWeekDoesNotReturnServerError() throws Exception {
        HttpResponse<String> response = get("/nutrition-summary?weekStart=invalid");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains(
                "The requested week was invalid. Showing this week instead.",
                "Next Week",
                "aria-disabled=\"true\""
        );
    }

    @Test
    void pageAggregatesOnlyCurrentProfileAndDisplaysGoalsAndDailyLinks() throws Exception {
        insertGoal("200", "20", "40", "10");
        insertFoodEntry(profileId, LocalDateTime.of(2026, 7, 20, 8, 0), "700", "70", "140", "35");
        insertFoodEntry(profileId, LocalDateTime.of(2026, 7, 20, 12, 0), "0", "0", "0", "0");
        insertFoodEntry(profileId, LocalDateTime.of(2026, 7, 22, 18, 0), "701", "71", "141", "36");
        insertForeignProfileEntry();

        HttpResponse<String> response = get("/nutrition-summary?weekStart=2026-07-23");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains(
                "Jul 20–26, 2026",
                "2 of 7",
                ">3</dd>",
                "1401 kcal",
                "200 kcal/day",
                "20.1 g",
                "100%",
                "Logged",
                "No entries",
                "/food?date=2026-07-20",
                "/food?date=2026-07-26",
                "Compared with your current daily nutrition goals"
        );
        assertThat(response.body()).doesNotContain("9999 kcal", "Foreign profile");
    }

    @Test
    void databaseRangeIncludesMondayAndSundayButExcludesAdjacentWeeksAndOtherProfiles() {
        LocalDate monday = LocalDate.of(2026, 7, 20);
        insertFoodEntry(profileId, monday.minusDays(1).atTime(23, 59, 59), "10", "1", "1", "1", "Previous Sunday");
        insertFoodEntry(profileId, monday.atStartOfDay(), "20", "2", "2", "2", "Included Monday");
        insertFoodEntry(profileId, monday.plusDays(6).atTime(23, 59, 59), "30", "3", "3", "3", "Included Sunday");
        insertFoodEntry(profileId, monday.plusWeeks(1).atStartOfDay(), "40", "4", "4", "4", "Next Monday");
        insertForeignProfileEntry(monday.plusDays(2).atTime(12, 0), "Foreign profile");

        List<FoodEntryResponse> entries = foodEntryService.listEntriesForDateRange(monday, monday.plusWeeks(1));

        assertThat(entries).extracting(FoodEntryResponse::foodName)
                .containsExactlyInAnyOrder("Included Monday", "Included Sunday");
    }

    @Test
    void currentProfileGoalIsIsolatedFromForeignProfileGoal() {
        insertGoal("2000", "120", "250", "70");
        insertForeignProfileGoal();

        assertThat(nutritionGoalService.getCurrentGoal()).get()
                .extracting(NutritionGoalResponse::dailyCalories)
                .isEqualTo(new BigDecimal("2000.00"));

        jdbcTemplate.update("DELETE FROM nutrition_goals WHERE profile_id = ?", profileId);

        assertThat(nutritionGoalService.getCurrentGoal()).isEmpty();
    }

    @Test
    void currentWeekNextWeekIsDisabledWithoutHref() throws Exception {
        HttpResponse<String> response = get("/nutrition-summary");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(Pattern.compile("<span[^>]*aria-disabled=\"true\"[^>]*>Next Week</span>")
                .matcher(response.body()).find()).isTrue();
        assertThat(Pattern.compile("<a[^>]*href=[^>]*>Next Week</a>")
                .matcher(response.body()).find()).isFalse();
    }

    private HttpResponse<String> get(String path) throws IOException, InterruptedException {
        return httpClient.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString()
        );
    }

    private void insertGoal(String calories, String protein, String carbohydrate, String fat) {
        jdbcTemplate.update("""
                INSERT INTO nutrition_goals (
                    profile_id, daily_calories, daily_protein_grams,
                    daily_carbohydrate_grams, daily_fat_grams, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                profileId, new BigDecimal(calories), new BigDecimal(protein),
                new BigDecimal(carbohydrate), new BigDecimal(fat),
                LocalDateTime.now(), LocalDateTime.now()
        );
    }

    private void insertFoodEntry(
            Long ownerProfileId,
            LocalDateTime eatenAt,
            String calories,
            String protein,
            String carbohydrate,
            String fat
    ) {
        insertFoodEntry(ownerProfileId, eatenAt, calories, protein, carbohydrate, fat, "Test food");
    }

    private void insertFoodEntry(
            Long ownerProfileId,
            LocalDateTime eatenAt,
            String calories,
            String protein,
            String carbohydrate,
            String fat,
            String foodName
    ) {
        jdbcTemplate.update("""
                INSERT INTO food_entries (
                    profile_id, food_name, amount, unit, calories, protein_grams,
                    carbohydrate_grams, fat_grams, fiber_grams, meal_type, eaten_at, created_at
                ) VALUES (?, ?, 1, 'serving', ?, ?, ?, ?, 0, 'BREAKFAST', ?, ?)
                """,
                ownerProfileId, foodName, new BigDecimal(calories), new BigDecimal(protein),
                new BigDecimal(carbohydrate), new BigDecimal(fat), eatenAt, eatenAt
        );
    }

    private void insertForeignProfileEntry() {
        insertForeignProfileEntry(LocalDateTime.of(2026, 7, 21, 9, 0), "Foreign profile");
    }

    private void insertForeignProfileEntry(LocalDateTime eatenAt, String foodName) {
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 0");
        try {
            insertFoodEntry(
                    999L,
                    eatenAt,
                    "9999",
                    "999",
                    "999",
                    "999",
                    foodName
            );
        } finally {
            jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 1");
        }
    }

    private void insertForeignProfileGoal() {
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 0");
        try {
            jdbcTemplate.update("""
                    INSERT INTO nutrition_goals (
                        profile_id, daily_calories, daily_protein_grams,
                        daily_carbohydrate_grams, daily_fat_grams, created_at, updated_at
                    ) VALUES (999, 9999, 999, 999, 999, ?, ?)
                    """, LocalDateTime.now(), LocalDateTime.now());
        } finally {
            jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 1");
        }
    }

    private int count(String text, String token) {
        return (text.length() - text.replace(token, "").length()) / token.length();
    }
}

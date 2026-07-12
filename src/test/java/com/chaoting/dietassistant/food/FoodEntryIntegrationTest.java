package com.chaoting.dietassistant.food;

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
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class FoodEntryIntegrationTest {

    @Container
    @ServiceConnection
    static final MySQLContainer<?> mysql = new MySQLContainer<>(DockerImageName.parse("mysql:8.4"));

    @LocalServerPort
    private int port;

    @Autowired
    private FoodEntryRepository foodEntryRepository;

    @Autowired
    private ProfileService profileService;

    private HttpClient httpClient;

    @BeforeEach
    void setUp() {
        foodEntryRepository.deleteAll();
        createProfile();
        httpClient = HttpClient.newBuilder()
                .cookieHandler(new CookieManager())
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Test
    void getFoodShowsFormMealTypesEntriesTotalsAndDefaultEatenAt() throws IOException, InterruptedException {
        LocalDate today = LocalDate.now();
        saveEntry("Breakfast oats", "300.00", "20.00", "40.00", "6.00", "8.00", today.atTime(8, 0));
        saveEntry("Lunch salad", "450.00", "30.00", "35.00", "18.00", "10.00", today.atTime(12, 30));

        HttpResponse<String> response = get("/food");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains(
                "Food",
                "Save food entry",
                "value=\"BREAKFAST\"",
                "value=\"LUNCH\"",
                "value=\"DINNER\"",
                "value=\"SNACK\"",
                "Breakfast oats",
                "Lunch salad",
                "750.00",
                "50.00",
                "75.00",
                "24.00",
                "18.00",
                "value=\"" + today + "T"
        );
        assertThat(response.body()).containsSubsequence("Lunch salad", "Breakfast oats");
    }

    @Test
    void getFoodShowsEmptyStateAndZeroTotalsWhenNoEntriesExist() throws IOException, InterruptedException {
        HttpResponse<String> response = get("/food");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("No food entries recorded today.");
        assertThat(response.body()).contains("0");
    }

    @Test
    void postFoodCreatesEntryWithZeroCaloriesThenDeleteRemovesIt() throws IOException, InterruptedException {
        HttpResponse<String> createResponse = post("/food", validForm(LocalDateTime.now().minusMinutes(5), "0.00"));

        assertThat(createResponse.statusCode()).isEqualTo(200);
        assertThat(createResponse.uri().getPath()).startsWith("/food");
        assertThat(createResponse.body()).contains("Food entry saved.");
        assertThat(foodEntryRepository.count()).isEqualTo(1);

        FoodEntry savedEntry = foodEntryRepository.findAll().getFirst();
        assertThat(savedEntry.getFoodName()).isEqualTo("Apple");
        assertThat(savedEntry.getAmount()).isEqualByComparingTo("1.00");
        assertThat(savedEntry.getUnit()).isEqualTo("piece");
        assertThat(savedEntry.getCalories()).isEqualByComparingTo("0.00");
        assertThat(savedEntry.getProteinGrams()).isEqualByComparingTo("0.50");
        assertThat(savedEntry.getCarbohydrateGrams()).isEqualByComparingTo("25.00");
        assertThat(savedEntry.getFatGrams()).isEqualByComparingTo("0.30");
        assertThat(savedEntry.getFiberGrams()).isEqualByComparingTo("4.00");
        assertThat(savedEntry.getMealType()).isEqualTo(MealType.SNACK);
        assertThat(savedEntry.getNotes()).isEqualTo("Afternoon snack");
        assertThat(savedEntry.getCreatedAt()).isNotNull();

        HttpResponse<String> deleteResponse = post("/food/" + savedEntry.getId() + "/delete", Map.of());

        assertThat(deleteResponse.statusCode()).isEqualTo(200);
        assertThat(deleteResponse.uri().getPath()).startsWith("/food");
        assertThat(deleteResponse.body()).contains("Food entry deleted.");
        assertThat(foodEntryRepository.count()).isZero();
    }

    @Test
    void postFoodReturnsFormWhenValidationFails() throws IOException, InterruptedException {
        Map<String, String> formValues = validForm(LocalDateTime.now().plusDays(1), "-1.00");
        formValues.put("foodName", "");
        formValues.put("amount", "0");
        formValues.put("unit", "");
        formValues.put("proteinGrams", "-0.01");

        HttpResponse<String> response = post("/food", formValues);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("must not be blank");
        assertThat(response.body()).contains("must be greater than 0");
        assertThat(response.body()).contains("must be greater than or equal to 0");
        assertThat(response.body()).contains("must be a date in the past or in the present");
        assertThat(foodEntryRepository.count()).isZero();
    }

    @Test
    void repositoryCurrentDayQueryUsesInclusiveStartAndExclusiveEnd() {
        Long profileId = profileService.getProfile().orElseThrow().id();
        LocalDate today = LocalDate.now();
        saveEntry("Previous day", "100.00", "1.00", "1.00", "1.00", "1.00", today.minusDays(1).atTime(23, 59));
        saveEntry("Start boundary", "200.00", "2.00", "2.00", "2.00", "2.00", today.atStartOfDay());
        saveEntry("End minus one", "300.00", "3.00", "3.00", "3.00", "3.00", today.plusDays(1).atStartOfDay().minusNanos(1000));
        saveEntry("Exclusive end", "400.00", "4.00", "4.00", "4.00", "4.00", today.plusDays(1).atStartOfDay());

        var entries = foodEntryRepository.findByProfileIdAndEatenAtGreaterThanEqualAndEatenAtLessThanOrderByEatenAtDescIdDesc(
                profileId,
                today.atStartOfDay(),
                today.plusDays(1).atStartOfDay()
        );

        assertThat(entries).extracting(FoodEntry::getFoodName).containsExactly("End minus one", "Start boundary");
    }

    @Test
    void deleteMissingFoodEntryDoesNotDeleteExistingEntries() throws IOException, InterruptedException {
        saveEntry("Keep me", "100.00", "1.00", "1.00", "1.00", "1.00", LocalDateTime.now().minusMinutes(10));

        HttpResponse<String> response = post("/food/999999/delete", Map.of());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(foodEntryRepository.count()).isEqualTo(1);
    }

    private void createProfile() {
        ProfileRequest request = new ProfileRequest();
        request.setBirthYear(1988);
        request.setHeightCm(new BigDecimal("175.50"));
        request.setWeightKg(new BigDecimal("72.25"));
        profileService.save(request);
    }

    private FoodEntry saveEntry(
            String foodName,
            String calories,
            String proteinGrams,
            String carbohydrateGrams,
            String fatGrams,
            String fiberGrams,
            LocalDateTime eatenAt
    ) {
        Long profileId = profileService.getProfile().orElseThrow().id();
        FoodEntry foodEntry = new FoodEntry();
        foodEntry.setProfileId(profileId);
        foodEntry.setFoodName(foodName);
        foodEntry.setAmount(new BigDecimal("1.00"));
        foodEntry.setUnit("serving");
        foodEntry.setCalories(new BigDecimal(calories));
        foodEntry.setProteinGrams(new BigDecimal(proteinGrams));
        foodEntry.setCarbohydrateGrams(new BigDecimal(carbohydrateGrams));
        foodEntry.setFatGrams(new BigDecimal(fatGrams));
        foodEntry.setFiberGrams(new BigDecimal(fiberGrams));
        foodEntry.setMealType(MealType.SNACK);
        foodEntry.setEatenAt(eatenAt);
        foodEntry.setCreatedAt(LocalDateTime.now());
        return foodEntryRepository.save(foodEntry);
    }

    private Map<String, String> validForm(LocalDateTime eatenAt, String calories) {
        Map<String, String> formValues = new LinkedHashMap<>();
        formValues.put("foodName", "Apple");
        formValues.put("amount", "1.00");
        formValues.put("unit", "piece");
        formValues.put("calories", calories);
        formValues.put("proteinGrams", "0.50");
        formValues.put("carbohydrateGrams", "25.00");
        formValues.put("fatGrams", "0.30");
        formValues.put("fiberGrams", "4.00");
        formValues.put("mealType", "SNACK");
        formValues.put("eatenAt", eatenAt.withSecond(0).withNano(0).toString());
        formValues.put("notes", "Afternoon snack");
        return formValues;
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

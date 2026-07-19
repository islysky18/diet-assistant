package com.chaoting.dietassistant.food;

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
    private SavedFoodRepository savedFoodRepository;

    @Autowired
    private ProfileService profileService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private HttpClient httpClient;

    @BeforeEach
    void setUp() {
        foodEntryRepository.deleteAll();
        savedFoodRepository.deleteAll();
        createProfile();
        httpClient = HttpClient.newBuilder()
                .cookieHandler(new CookieManager())
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Test
    void getFoodsShowsFormAndOnlyActiveSavedFoods() throws IOException, InterruptedException {
        saveSavedFood("Active oats", "Quaker", true);
        saveSavedFood("Inactive bar", "Brand", false);

        HttpResponse<String> response = get("/foods");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("Foods", "Save food", "Active oats", "Quaker", "Record intake", "Edit");
        assertThat(response.body()).doesNotContain("Inactive bar");
    }

    @Test
    void postFoodsCreatesSavedFoodThenEditAndDeactivateWork() throws IOException, InterruptedException {
        HttpResponse<String> createResponse = post("/foods", savedFoodForm(
                "Greek yogurt",
                "Chobani",
                "1.00",
                "cup",
                "227.00",
                "100.00"
        ));

        assertThat(createResponse.statusCode()).isEqualTo(200);
        assertThat(createResponse.uri().getPath()).startsWith("/foods");
        assertThat(createResponse.body()).contains("Saved food created.");
        assertThat(savedFoodRepository.count()).isEqualTo(1);

        SavedFood savedFood = savedFoodRepository.findAll().getFirst();
        assertThat(savedFood.getName()).isEqualTo("Greek yogurt");
        assertThat(savedFood.getBrand()).isEqualTo("Chobani");
        assertThat(savedFood.getReferenceUnit()).isEqualTo("cup");
        assertThat(savedFood.getReferenceWeightGrams()).isEqualByComparingTo("227.00");

        HttpResponse<String> editResponse = get("/foods/" + savedFood.getId() + "/edit");

        assertThat(editResponse.statusCode()).isEqualTo(200);
        assertThat(editResponse.body()).contains("Edit Food", "Greek yogurt", "Chobani");

        HttpResponse<String> updateResponse = post("/foods/" + savedFood.getId(), savedFoodForm(
                "Greek yogurt updated",
                "Chobani",
                "2.00",
                "cups",
                "454.00",
                "220.00"
        ));

        assertThat(updateResponse.statusCode()).isEqualTo(200);
        assertThat(updateResponse.body()).contains("Saved food updated.");
        SavedFood updated = savedFoodRepository.findById(savedFood.getId()).orElseThrow();
        assertThat(updated.getName()).isEqualTo("Greek yogurt updated");
        assertThat(updated.getReferenceAmount()).isEqualByComparingTo("2.00");
        assertThat(updated.getCalories()).isEqualByComparingTo("220.00");

        HttpResponse<String> deactivateResponse = post("/foods/" + savedFood.getId() + "/deactivate", Map.of());

        assertThat(deactivateResponse.statusCode()).isEqualTo(200);
        assertThat(deactivateResponse.body()).contains("Saved food deactivated.");
        assertThat(savedFoodRepository.findById(savedFood.getId()).orElseThrow().isActive()).isFalse();
        assertThat(get("/foods").body()).doesNotContain("Greek yogurt updated");
    }

    @Test
    void invalidEditPostForMissingSavedFoodRedirectsWithoutServerError() throws IOException, InterruptedException {
        HttpResponse<String> response = post("/foods/999999", invalidSavedFoodForm());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.uri().getPath()).startsWith("/foods");
        assertThat(response.body()).contains("Saved food not found.");
        assertThat(response.body()).doesNotContain("Edit Food");
    }

    @Test
    void invalidEditPostForForeignSavedFoodRedirectsWithoutServerError() throws IOException, InterruptedException {
        insertForeignSavedFood(999999L);

        HttpResponse<String> response = post("/foods/999999", invalidSavedFoodForm());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.uri().getPath()).startsWith("/foods");
        assertThat(response.body()).contains("Saved food not found.");
        assertThat(response.body()).doesNotContain("Edit Food");
    }

    @Test
    void postFoodsRejectsDecimalValuesThatDoNotFitDatabaseScale() throws IOException, InterruptedException {
        Map<String, String> formValues = savedFoodForm(
                "Greek yogurt",
                "Chobani",
                "0.001",
                "cup",
                "227.001",
                "123456789.00"
        );
        formValues.put("proteinGrams", "4.001");

        HttpResponse<String> response = post("/foods", formValues);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("must have up to 8 digits before the decimal and 2 after");
        assertThat(savedFoodRepository.count()).isZero();
    }

    @Test
    void getFoodShowsSavedFoodDropdownLegacyEntriesTotalsAndDefaultEatenAt() throws IOException, InterruptedException {
        LocalDate today = LocalDate.now();
        SavedFood savedFood = saveSavedFood("Bread", "Bakery", true);
        saveLegacyEntry("Legacy apple", "95.00", "0.50", "25.00", "0.30", "4.00", today.atTime(8, 0));

        HttpResponse<String> response = get("/food?savedFoodId=" + savedFood.getId());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains(
                "Food",
                "Save food entry",
                "Bakery - Bread",
                "Legacy apple",
                "95.00",
                "0.50",
                "25.00",
                "0.30",
                "4.00",
                "value=\"" + today + "T"
        );
    }

    @Test
    void postFoodCreatesCalculatedEntryWithSnapshotsThenDeleteRemovesIt() throws IOException, InterruptedException {
        SavedFood savedFood = saveSavedFood("Bread", "Bakery", true);

        HttpResponse<String> createResponse = post("/food", foodEntryForm(
                savedFood.getId(),
                "60.00",
                "grams",
                LocalDateTime.now().minusMinutes(5)
        ));

        assertThat(createResponse.statusCode()).isEqualTo(200);
        assertThat(createResponse.uri().getPath()).startsWith("/food");
        assertThat(createResponse.body()).contains("Food entry saved.");
        assertThat(foodEntryRepository.count()).isEqualTo(1);

        FoodEntry savedEntry = foodEntryRepository.findAll().getFirst();
        assertThat(savedEntry.getSavedFoodId()).isEqualTo(savedFood.getId());
        assertThat(savedEntry.getSavedFoodName()).isEqualTo("Bread");
        assertThat(savedEntry.getSavedFoodBrand()).isEqualTo("Bakery");
        assertThat(savedEntry.getSavedFoodReferenceAmount()).isEqualByComparingTo("1.00");
        assertThat(savedEntry.getSavedFoodReferenceUnit()).isEqualTo("slice");
        assertThat(savedEntry.getSavedFoodReferenceWeightGrams()).isEqualByComparingTo("40.00");
        assertThat(savedEntry.getFoodName()).isEqualTo("Bread");
        assertThat(savedEntry.getAmount()).isEqualByComparingTo("60.00");
        assertThat(savedEntry.getUnit()).isEqualTo("grams");
        assertThat(savedEntry.getCalculationMultiplier()).isEqualByComparingTo("1.50000000");
        assertThat(savedEntry.getCalories()).isEqualByComparingTo("150.00");
        assertThat(savedEntry.getProteinGrams()).isEqualByComparingTo("6.00");
        assertThat(savedEntry.getCarbohydrateGrams()).isEqualByComparingTo("30.00");
        assertThat(savedEntry.getFatGrams()).isEqualByComparingTo("1.50");
        assertThat(savedEntry.getFiberGrams()).isEqualByComparingTo("3.00");
        assertThat(savedEntry.getMealType()).isEqualTo(MealType.SNACK);
        assertThat(savedEntry.getNotes()).isEqualTo("Afternoon snack");

        HttpResponse<String> deleteResponse = post("/food/" + savedEntry.getId() + "/delete", Map.of());

        assertThat(deleteResponse.statusCode()).isEqualTo(200);
        assertThat(deleteResponse.body()).contains("Food entry deleted.");
        assertThat(foodEntryRepository.count()).isZero();
    }

    @Test
    void savedFoodEditsDoNotChangeHistoricalFoodEntryTotals() throws IOException, InterruptedException {
        SavedFood savedFood = saveSavedFood("Bread", "Bakery", true);
        post("/food", foodEntryForm(savedFood.getId(), "1.00", "slice", LocalDateTime.now().minusMinutes(5)));

        post("/foods/" + savedFood.getId(), savedFoodForm(
                "Bread edited",
                "Bakery",
                "1.00",
                "slice",
                "40.00",
                "999.00"
        ));

        HttpResponse<String> response = get("/food");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("100.00");
        assertThat(response.body()).doesNotContain("999.00");
    }

    @Test
    void postFoodReturnsFormWhenValidationFails() throws IOException, InterruptedException {
        SavedFood savedFood = saveSavedFood("Soup", "Kitchen", true);
        savedFood.setReferenceWeightGrams(null);
        savedFoodRepository.save(savedFood);
        Map<String, String> formValues = foodEntryForm(savedFood.getId(), "0", "g", LocalDateTime.now().plusDays(1));

        HttpResponse<String> response = post("/food", formValues);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("must be greater than 0");
        assertThat(response.body()).contains("must be a date in the past or in the present");
        assertThat(foodEntryRepository.count()).isZero();

        formValues.put("amount", "1.00");
        formValues.put("eatenAt", LocalDateTime.now().minusMinutes(5).withSecond(0).withNano(0).toString());
        HttpResponse<String> incompatibleUnitResponse = post("/food", formValues);

        assertThat(incompatibleUnitResponse.statusCode()).isEqualTo(200);
        assertThat(incompatibleUnitResponse.body()).contains("Use the saved food reference unit, or grams when a reference weight is saved.");
        assertThat(foodEntryRepository.count()).isZero();
    }

    @Test
    void postFoodRejectsConsumedAmountThatDoesNotFitDatabaseScale() throws IOException, InterruptedException {
        SavedFood savedFood = saveSavedFood("Bread", "Bakery", true);
        Map<String, String> fractionalFormValues = foodEntryForm(
                savedFood.getId(),
                "0.001",
                "slice",
                LocalDateTime.now().minusMinutes(5)
        );

        HttpResponse<String> fractionalResponse = post("/food", fractionalFormValues);

        assertThat(fractionalResponse.statusCode()).isEqualTo(200);
        assertThat(fractionalResponse.body()).contains("must have up to 8 digits before the decimal and 2 after");
        assertThat(foodEntryRepository.count()).isZero();

        Map<String, String> largeFormValues = foodEntryForm(
                savedFood.getId(),
                "123456789.00",
                "slice",
                LocalDateTime.now().minusMinutes(5)
        );

        HttpResponse<String> largeResponse = post("/food", largeFormValues);

        assertThat(largeResponse.statusCode()).isEqualTo(200);
        assertThat(largeResponse.body()).contains("must have up to 8 digits before the decimal and 2 after");
        assertThat(foodEntryRepository.count()).isZero();
    }

    @Test
    void repositoryCurrentDayQueryUsesInclusiveStartAndExclusiveEnd() {
        Long profileId = profileService.getProfile().orElseThrow().id();
        LocalDate today = LocalDate.now();
        saveLegacyEntry("Previous day", "100.00", "1.00", "1.00", "1.00", "1.00", today.minusDays(1).atTime(23, 59));
        saveLegacyEntry("Start boundary", "200.00", "2.00", "2.00", "2.00", "2.00", today.atStartOfDay());
        saveLegacyEntry("End minus one", "300.00", "3.00", "3.00", "3.00", "3.00", today.plusDays(1).atStartOfDay().minusNanos(1000));
        saveLegacyEntry("Exclusive end", "400.00", "4.00", "4.00", "4.00", "4.00", today.plusDays(1).atStartOfDay());

        var entries = foodEntryRepository.findByProfileIdAndEatenAtGreaterThanEqualAndEatenAtLessThanOrderByEatenAtDescIdDesc(
                profileId,
                today.atStartOfDay(),
                today.plusDays(1).atStartOfDay()
        );

        assertThat(entries).extracting(FoodEntry::getFoodName).containsExactly("End minus one", "Start boundary");
    }

    private void createProfile() {
        ProfileRequest request = new ProfileRequest();
        request.setBirthYear(1988);
        request.setHeightCm(new BigDecimal("175.50"));
        request.setWeightKg(new BigDecimal("72.25"));
        profileService.save(request);
    }

    private SavedFood saveSavedFood(String name, String brand, boolean active) {
        Long profileId = profileService.getProfile().orElseThrow().id();
        SavedFood savedFood = new SavedFood();
        savedFood.setProfileId(profileId);
        savedFood.setName(name);
        savedFood.setBrand(brand);
        savedFood.setReferenceAmount(new BigDecimal("1.00"));
        savedFood.setReferenceUnit("slice");
        savedFood.setReferenceWeightGrams(new BigDecimal("40.00"));
        savedFood.setCalories(new BigDecimal("100.00"));
        savedFood.setProteinGrams(new BigDecimal("4.00"));
        savedFood.setCarbohydrateGrams(new BigDecimal("20.00"));
        savedFood.setFatGrams(new BigDecimal("1.00"));
        savedFood.setFiberGrams(new BigDecimal("2.00"));
        savedFood.setNotes("Saved note");
        savedFood.setActive(active);
        savedFood.setCreatedAt(LocalDateTime.now());
        savedFood.setUpdatedAt(LocalDateTime.now());
        return savedFoodRepository.save(savedFood);
    }

    private void insertForeignSavedFood(Long id) {
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS=0");
        try {
            jdbcTemplate.update("""
                    INSERT INTO saved_foods (
                        id,
                        profile_id,
                        name,
                        brand,
                        reference_amount,
                        reference_unit,
                        reference_weight_grams,
                        calories,
                        protein_grams,
                        carbohydrate_grams,
                        fat_grams,
                        fiber_grams,
                        notes,
                        active,
                        created_at,
                        updated_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    id,
                    999999L,
                    "Foreign bread",
                    "Other",
                    new BigDecimal("1.00"),
                    "slice",
                    new BigDecimal("40.00"),
                    new BigDecimal("100.00"),
                    new BigDecimal("4.00"),
                    new BigDecimal("20.00"),
                    new BigDecimal("1.00"),
                    new BigDecimal("2.00"),
                    null,
                    true,
                    LocalDateTime.now(),
                    LocalDateTime.now()
            );
        } finally {
            jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS=1");
        }
    }

    private FoodEntry saveLegacyEntry(
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

    private Map<String, String> savedFoodForm(
            String name,
            String brand,
            String referenceAmount,
            String referenceUnit,
            String referenceWeightGrams,
            String calories
    ) {
        Map<String, String> formValues = new LinkedHashMap<>();
        formValues.put("name", name);
        formValues.put("brand", brand);
        formValues.put("referenceAmount", referenceAmount);
        formValues.put("referenceUnit", referenceUnit);
        formValues.put("referenceWeightGrams", referenceWeightGrams);
        formValues.put("calories", calories);
        formValues.put("proteinGrams", "4.00");
        formValues.put("carbohydrateGrams", "20.00");
        formValues.put("fatGrams", "1.00");
        formValues.put("fiberGrams", "2.00");
        formValues.put("notes", "Saved note");
        return formValues;
    }

    private Map<String, String> invalidSavedFoodForm() {
        return savedFoodForm(
                "",
                "Brand",
                "0.001",
                "slice",
                "40.00",
                "100.00"
        );
    }

    private Map<String, String> foodEntryForm(Long savedFoodId, String amount, String unit, LocalDateTime eatenAt) {
        Map<String, String> formValues = new LinkedHashMap<>();
        formValues.put("savedFoodId", savedFoodId.toString());
        formValues.put("amount", amount);
        formValues.put("unit", unit);
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

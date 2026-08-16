package com.chaoting.dietassistant.food;

import com.chaoting.dietassistant.profile.ProfileRequest;
import com.chaoting.dietassistant.profile.ProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.math.BigDecimal;
import java.net.CookieManager;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class FoodEntryIntegrationTest {

    private static final Path TEST_PENDING_ROOT = createTestPendingRoot();

    @Container
    @ServiceConnection
    static final MySQLContainer<?> mysql = new MySQLContainer<>(DockerImageName.parse("mysql:8.4"));

    @DynamicPropertySource
    static void pendingImportProperties(DynamicPropertyRegistry registry) {
        registry.add("diet-assistant.food-import.pending-directory", TEST_PENDING_ROOT::toString);
    }

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
    private CookieManager cookieManager;

    @BeforeEach
    void setUp() {
        foodEntryRepository.deleteAll();
        savedFoodRepository.deleteAll();
        createProfile();
        cookieManager = new CookieManager();
        httpClient = HttpClient.newBuilder()
                .cookieHandler(cookieManager)
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
    void manualDuplicateReviewCanUseExistingWithoutCreatingOrChangingIt() throws IOException, InterruptedException {
        SavedFood existing = saveSavedFood("Diet Coke", "Coca-Cola", true);
        Map<String, String> duplicate = savedFoodForm(
                " diet   coke ", "Coca Cola", "1.0", "slice", "40.0", "100.0");
        duplicate.put("notes", "Different recognition note");

        HttpResponse<String> review = post("/foods", duplicate);

        assertThat(review.uri().getPath()).startsWith("/foods/duplicates/");
        assertThat(review.body()).contains("Possible duplicate found", "Use existing Coca-Cola - Diet Coke");
        assertThat(savedFoodRepository.count()).isEqualTo(1);

        HttpResponse<String> result = post(review.uri().getPath() + "/use-existing",
                Map.of("candidateId", existing.getId().toString()));
        assertThat(result.body()).contains("Existing saved food kept; no duplicate was created.");
        assertThat(savedFoodRepository.count()).isEqualTo(1);
        assertThat(savedFoodRepository.findById(existing.getId()).orElseThrow().isActive()).isTrue();
    }

    @Test
    void manualDuplicateCreateAnywayIsSingleUseAndBackPreservesInput() throws IOException, InterruptedException {
        saveSavedFood("Diet Coke", "Coca-Cola", true);
        Map<String, String> duplicate = savedFoodForm(
                "Diet Coke", "Coca-Cola", "1.00", "slice", "40.00", "100.00");

        HttpResponse<String> backReview = post("/foods", duplicate);
        HttpResponse<String> back = post(backReview.uri().getPath() + "/back", Map.of());
        assertThat(back.body()).contains("value=\"Diet Coke\"", "value=\"Coca-Cola\"");
        assertThat(savedFoodRepository.count()).isEqualTo(1);

        HttpResponse<String> createReview = post("/foods", duplicate);
        String createPath = createReview.uri().getPath() + "/create-anyway";
        HttpResponse<String> created = post(createPath, Map.of());
        HttpResponse<String> repeated = post(createPath, Map.of());

        assertThat(created.body()).contains("Saved food created after duplicate review.");
        assertThat(repeated.body()).contains("Duplicate review expired or was already completed.");
        assertThat(savedFoodRepository.count()).isEqualTo(2);
    }

    @Test
    void inactiveExactMatchDoesNotBlockManualCreation() throws IOException, InterruptedException {
        saveSavedFood("Diet Coke", "Coca-Cola", false);

        HttpResponse<String> response = post("/foods", savedFoodForm(
                "Diet Coke", "Coca-Cola", "1.00", "slice", "40.00", "100.00"));

        assertThat(response.uri().getPath()).startsWith("/foods");
        assertThat(response.body()).contains("Saved food created.");
        assertThat(savedFoodRepository.count()).isEqualTo(2);
    }

    @Test
    void duplicateCandidateMustStillBeActiveAndMatchWhenUseExistingIsSubmitted()
            throws IOException, InterruptedException {
        SavedFood existing = saveSavedFood("Diet Coke", "Coca-Cola", true);
        HttpResponse<String> review = post("/foods", savedFoodForm(
                "Diet Coke", "Coca-Cola", "1.00", "slice", "40.00", "100.00"));
        post("/foods/" + existing.getId() + "/deactivate", Map.of());

        HttpResponse<String> result = post(review.uri().getPath() + "/use-existing",
                Map.of("candidateId", existing.getId().toString()));

        assertThat(result.uri().getPath()).isEqualTo("/foods");
        assertThat(result.body()).contains("no longer an active duplicate", "value=\"Diet Coke\"");
        assertThat(savedFoodRepository.count()).isEqualTo(1);
        assertThat(savedFoodRepository.findById(existing.getId()).orElseThrow().isActive()).isFalse();
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
    void photoImportDoesNotSaveUntilManualReviewIsConfirmedAndThenDeletesPendingFiles()
            throws IOException, InterruptedException {
        HttpResponse<String> uploadResponse = postMultipartNutritionPhoto();

        assertThat(uploadResponse.statusCode()).isEqualTo(200);
        assertThat(uploadResponse.body()).contains("Pending import", "Confirm and save food");
        assertThat(savedFoodRepository.count()).isZero();
        var matcher = Pattern.compile("Import ID: ([0-9a-f-]{36})").matcher(uploadResponse.body());
        assertThat(matcher.find()).isTrue();
        String importId = matcher.group(1);
        Path importDirectory = TEST_PENDING_ROOT.resolve(importId);
        assertThat(importDirectory.resolve("nutrition-original.jpg")).exists();
        assertThat(importDirectory.resolve("nutrition.jpg")).exists();
        assertThat(importDirectory.resolve("metadata.json")).exists();

        HttpResponse<String> confirmResponse = post(
                "/foods/import/" + importId + "/confirm",
                savedFoodForm("Imported oats", "Example Brand", "1.00", "serving", "40.00", "150.00")
        );

        assertThat(confirmResponse.statusCode()).isEqualTo(200);
        assertThat(confirmResponse.body()).contains("Saved food created and pending photos deleted.", "Imported oats");
        assertThat(savedFoodRepository.count()).isEqualTo(1);
        assertThat(savedFoodRepository.findAll().getFirst().getProfileId())
                .isEqualTo(profileService.getProfile().orElseThrow().id());
        assertThat(importDirectory).doesNotExist();
    }

    @Test
    void photoDuplicateUseExistingCompletesImportWithoutCreatingAndCreateAnywayCannotRepeat()
            throws IOException, InterruptedException {
        SavedFood existing = saveSavedFood("Imported oats", "Example Brand", true);
        Map<String, String> form = savedFoodForm(
                "Imported oats", "Example Brand", "1.00", "slice", "40.00", "100.00");

        HttpResponse<String> useUpload = postMultipartNutritionPhoto();
        String useImportId = importId(useUpload.body());
        Path useDirectory = TEST_PENDING_ROOT.resolve(useImportId);
        HttpResponse<String> useReview = post("/foods/import/" + useImportId + "/confirm", form);
        assertThat(useReview.body()).contains("Possible duplicate found");
        assertThat(savedFoodRepository.count()).isEqualTo(1);

        HttpResponse<String> used = post(useReview.uri().getPath() + "/use-existing",
                Map.of("candidateId", existing.getId().toString()));
        assertThat(used.body()).contains("Existing saved food used and pending photos deleted.");
        assertThat(savedFoodRepository.count()).isEqualTo(1);
        assertThat(useDirectory).doesNotExist();

        HttpResponse<String> createUpload = postMultipartNutritionPhoto();
        String createImportId = importId(createUpload.body());
        Path createDirectory = TEST_PENDING_ROOT.resolve(createImportId);
        HttpResponse<String> createReview = post("/foods/import/" + createImportId + "/confirm", form);
        String createPath = createReview.uri().getPath() + "/create-anyway";

        assertThat(post(createPath, Map.of()).body()).contains("Saved food created after duplicate review.");
        assertThat(post(createPath, Map.of()).body()).contains("Duplicate review expired or was already completed.");
        assertThat(savedFoodRepository.count()).isEqualTo(2);
        assertThat(createDirectory).doesNotExist();
    }

    @Test
    void manualSavedFoodAllowsAllOptionalNutritionToRemainUnknown() throws IOException, InterruptedException {
        Map<String, String> form = savedFoodForm("Unknown food", "", "1.00", "serving", "", "");
        form.put("proteinGrams", "");
        form.put("carbohydrateGrams", "");
        form.put("fatGrams", "");
        form.put("fiberGrams", "");
        form.put("notes", "");

        HttpResponse<String> response = post("/foods", form);

        assertThat(response.body()).contains("Saved food created.");
        SavedFood saved = savedFoodRepository.findAll().getFirst();
        assertThat(saved.getBrand()).isNull();
        assertThat(saved.getReferenceWeightGrams()).isNull();
        assertThat(saved.getCalories()).isNull();
        assertThat(saved.getProteinGrams()).isNull();
        assertThat(saved.getCarbohydrateGrams()).isNull();
        assertThat(saved.getFatGrams()).isNull();
        assertThat(saved.getFiberGrams()).isNull();
        assertThat(saved.getNotes()).isNull();
    }

    @Test
    void manualSavedFoodPreservesExplicitZerosAndLegalDecimals() throws IOException, InterruptedException {
        Map<String, String> zeroForm = savedFoodForm("Zero drink", "Brand", "1.00", "can", "", "0");
        zeroForm.put("proteinGrams", "0.00");
        zeroForm.put("carbohydrateGrams", "0");
        zeroForm.put("fatGrams", "0.0");
        zeroForm.put("fiberGrams", "0.00");
        post("/foods", zeroForm);

        Map<String, String> decimalForm = savedFoodForm("Decimal food", "Brand", "1.25", "serving", "42.50", "123.45");
        decimalForm.put("proteinGrams", "6.75");
        decimalForm.put("carbohydrateGrams", "20.50");
        decimalForm.put("fatGrams", "3.25");
        decimalForm.put("fiberGrams", "1.50");
        post("/foods", decimalForm);

        SavedFood zero = savedFoodRepository.findAll().stream().filter(food -> food.getName().equals("Zero drink")).findFirst().orElseThrow();
        assertThat(List.of(zero.getCalories(), zero.getProteinGrams(), zero.getCarbohydrateGrams(), zero.getFatGrams(), zero.getFiberGrams()))
                .allSatisfy(value -> assertThat(value).isEqualByComparingTo(BigDecimal.ZERO));
        SavedFood decimal = savedFoodRepository.findAll().stream().filter(food -> food.getName().equals("Decimal food")).findFirst().orElseThrow();
        assertThat(decimal.getReferenceWeightGrams()).isEqualByComparingTo("42.50");
        assertThat(decimal.getCalories()).isEqualByComparingTo("123.45");
        assertThat(decimal.getProteinGrams()).isEqualByComparingTo("6.75");
        assertThat(decimal.getCarbohydrateGrams()).isEqualByComparingTo("20.50");
        assertThat(decimal.getFatGrams()).isEqualByComparingTo("3.25");
        assertThat(decimal.getFiberGrams()).isEqualByComparingTo("1.50");
    }

    @Test
    void invalidOptionalNutritionPreservesImportAndEditedFormWithoutSaving() throws IOException, InterruptedException {
        String importId = importId(postMultipartNutritionPhoto().body());
        Path importDirectory = TEST_PENDING_ROOT.resolve(importId);
        Map<String, String> form = savedFoodForm("Edited Diet Coke", "Coca-Cola", "1.00", "can", "", "0");
        form.put("proteinGrams", "-1");
        form.put("fiberGrams", "not-a-number");

        HttpResponse<String> response = post("/foods/import/" + importId + "/confirm", form);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains(
                "Edited Diet Coke",
                "value=\"20.00\"",
                "Failed to convert property value"
        );
        assertThat(savedFoodRepository.count()).isZero();
        assertThat(importDirectory.resolve("metadata.json")).exists();
        assertThat(importDirectory.resolve("nutrition.jpg")).exists();

        post("/foods/import/" + importId + "/cancel", Map.of());
    }

    @Test
    void dietCokeImportPreservesUnknownAndZeroValuesAndRepeatedConfirmIsIdempotent()
            throws IOException, InterruptedException {
        String importId = importId(postMultipartNutritionPhoto().body());
        Path importDirectory = TEST_PENDING_ROOT.resolve(importId);
        Map<String, String> form = savedFoodForm("Diet Coke", "Coca-Cola", "1", "can", "", "0");
        form.put("proteinGrams", "0");
        form.put("carbohydrateGrams", "0");
        form.put("fatGrams", "0");
        form.put("fiberGrams", "");

        post("/foods/import/" + importId + "/confirm", form);
        post("/foods/import/" + importId + "/confirm", form);

        assertThat(savedFoodRepository.count()).isEqualTo(1);
        SavedFood saved = savedFoodRepository.findAll().getFirst();
        assertThat(saved.getCalories()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(saved.getProteinGrams()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(saved.getCarbohydrateGrams()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(saved.getFatGrams()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(saved.getFiberGrams()).isNull();
        assertThat(saved.getReferenceWeightGrams()).isNull();
        assertThat(importDirectory).doesNotExist();
    }

    @Test
    void requiredSavedFoodFieldsRemainRequiredAndNegativeNutritionIsRejected() throws IOException, InterruptedException {
        Map<String, String> missingRequired = savedFoodForm("", "Brand", "", "", "", "");
        HttpResponse<String> missingResponse = post("/foods", missingRequired);
        assertThat(missingResponse.body()).contains("must not be blank", "must not be null");

        Map<String, String> negative = savedFoodForm("Negative", "Brand", "1", "serving", "", "-1");
        negative.put("proteinGrams", "-1");
        negative.put("carbohydrateGrams", "-1");
        negative.put("fatGrams", "-1");
        negative.put("fiberGrams", "-1");
        HttpResponse<String> negativeResponse = post("/foods", negative);

        assertThat(negativeResponse.body()).contains("must be greater than or equal to 0");
        assertThat(savedFoodRepository.count()).isZero();
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
    void recentFoodsAreUniqueNewestFirstExcludeInactiveAndQuickLogCopiesSnapshot()
            throws IOException, InterruptedException {
        SavedFood bread = saveSavedFood("Recent bread", "Bakery", true);
        SavedFood yogurt = saveSavedFood("Hidden yogurt", "Dairy", true);
        post("/food", foodEntryForm(bread.getId(), "1.00", "slice", LocalDateTime.now().minusHours(3)));
        post("/food", foodEntryForm(yogurt.getId(), "1.00", "slice", LocalDateTime.now().minusHours(2)));
        post("/food", foodEntryForm(bread.getId(), "2.00", "slice", LocalDateTime.now().minusHours(1)));
        FoodEntry source = foodEntryRepository.findAll().stream()
                .filter(entry -> entry.getSavedFoodId().equals(bread.getId()))
                .max(java.util.Comparator.comparing(FoodEntry::getEatenAt))
                .orElseThrow();
        yogurt.setActive(false);
        savedFoodRepository.save(yogurt);

        HttpResponse<String> page = get("/food");

        String recentSection = page.body().substring(
                page.body().indexOf("<h2>Recent Foods</h2>"),
                page.body().indexOf("<div class=\"content-grid\">")
        );
        assertThat(recentSection).contains("Recent bread", "2.00 slice", "Quick Log");
        assertThat(recentSection).doesNotContain("Hidden yogurt");
        assertThat(countOccurrences(recentSection, "Recent bread")).isEqualTo(1);

        bread.setName("Renamed bread");
        bread.setCalories(new BigDecimal("999.00"));
        savedFoodRepository.save(bread);
        LocalDate selectedDate = LocalDate.now().minusDays(2);

        HttpResponse<String> quickLog = post("/food/quick-log", Map.of(
                "sourceEntryId", source.getId().toString(),
                "date", selectedDate.toString()
        ));

        assertThat(quickLog.body()).contains("Food logged again.");
        FoodEntry copy = foodEntryRepository.findAll().stream()
                .max(java.util.Comparator.comparing(FoodEntry::getId))
                .orElseThrow();
        assertThat(copy.getFoodName()).isEqualTo("Recent bread");
        assertThat(copy.getAmount()).isEqualByComparingTo("2.00");
        assertThat(copy.getCalories()).isEqualByComparingTo("200.00");
        assertThat(copy.getMealType()).isEqualTo(source.getMealType());
        assertThat(copy.getNotes()).isNull();
        assertThat(copy.getEatenAt().toLocalDate()).isEqualTo(selectedDate);
    }

    @Test
    void quickLogInactiveFoodReturnsWarningWithoutCreatingEntry() throws IOException, InterruptedException {
        SavedFood savedFood = saveSavedFood("Inactive quick log", "Brand", true);
        post("/food", foodEntryForm(savedFood.getId(), "1.00", "slice", LocalDateTime.now().minusHours(1)));
        FoodEntry source = foodEntryRepository.findAll().getFirst();
        savedFood.setActive(false);
        savedFoodRepository.save(savedFood);

        HttpResponse<String> response = post("/food/quick-log", Map.of(
                "sourceEntryId", source.getId().toString(),
                "date", LocalDate.now().toString()
        ));

        assertThat(response.body()).contains("inactive or no longer exists");
        assertThat(foodEntryRepository.count()).isEqualTo(1);
    }

    @Test
    void quickLogDeletedFoodReturnsWarningWithoutCreatingEntry() throws IOException, InterruptedException {
        SavedFood savedFood = saveSavedFood("Deleted quick log", "Brand", true);
        post("/food", foodEntryForm(savedFood.getId(), "1.00", "slice", LocalDateTime.now().minusHours(1)));
        FoodEntry source = foodEntryRepository.findAll().getFirst();
        savedFoodRepository.delete(savedFood);
        savedFoodRepository.flush();

        HttpResponse<String> response = post("/food/quick-log", Map.of(
                "sourceEntryId", source.getId().toString(),
                "date", LocalDate.now().minusDays(1).toString()
        ));

        assertThat(response.body()).contains("inactive or no longer exists");
        assertThat(foodEntryRepository.count()).isEqualTo(1);
    }

    @Test
    void recentFoodsQueryReturnsAtMostFiveUniqueFoodsInDeterministicNewestOrder()
            throws IOException, InterruptedException {
        LocalDateTime baseTime = LocalDateTime.now().minusHours(8);
        SavedFood repeated = saveSavedFood("Repeated", "Brand", true);
        post("/food", foodEntryForm(repeated.getId(), "1.00", "slice", baseTime));
        post("/food", foodEntryForm(repeated.getId(), "2.00", "slice", baseTime.plusHours(7)));
        for (int index = 1; index <= 5; index++) {
            SavedFood food = saveSavedFood("Unique " + index, "Brand", true);
            post("/food", foodEntryForm(food.getId(), "1.00", "slice", baseTime.plusHours(index)));
        }
        saveLegacyEntry("Legacy without saved food", "10.00", "1.00", "1.00", "1.00", "1.00",
                baseTime.plusHours(9));
        insertForeignRecentFood(888001L, 888002L, baseTime.plusHours(10));
        Long profileId = profileService.getProfile().orElseThrow().id();

        List<FoodEntry> recent = foodEntryRepository.findRecentUniqueSavedFoodEntries(
                profileId, PageRequest.of(0, 5));

        assertThat(recent).hasSize(5);
        assertThat(recent).extracting(FoodEntry::getFoodName)
                .containsExactly("Repeated", "Unique 5", "Unique 4", "Unique 3", "Unique 2");
        assertThat(recent.getFirst().getAmount()).isEqualByComparingTo("2.00");
        assertThat(recent).extracting(FoodEntry::getSavedFoodId).doesNotContainNull();
        assertThat(recent).extracting(FoodEntry::getFoodName).doesNotContain("Foreign recent food");
    }

    @Test
    void foodEntryAndSummariesHandleNullableSavedFoodNutritionWithoutChangingIt()
            throws IOException, InterruptedException {
        SavedFood savedFood = saveSavedFood("Unknown nutrition", "Brand", true);
        savedFood.setCalories(BigDecimal.ZERO);
        savedFood.setProteinGrams(null);
        savedFood.setCarbohydrateGrams(null);
        savedFood.setFatGrams(null);
        savedFood.setFiberGrams(null);
        savedFoodRepository.save(savedFood);

        HttpResponse<String> createResponse = post("/food", foodEntryForm(
                savedFood.getId(), "2.00", "slice", LocalDateTime.now().minusMinutes(5)
        ));
        HttpResponse<String> dailyResponse = get("/food");
        HttpResponse<String> weeklyResponse = get("/nutrition-summary");

        assertThat(createResponse.body()).contains("Food entry saved.");
        FoodEntry entry = foodEntryRepository.findAll().getFirst();
        assertThat(entry.getCalories()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(entry.getProteinGrams()).isNull();
        assertThat(entry.getCarbohydrateGrams()).isNull();
        assertThat(entry.getFatGrams()).isNull();
        assertThat(entry.getFiberGrams()).isNull();
        assertThat(dailyResponse.statusCode()).isEqualTo(200);
        assertThat(weeklyResponse.statusCode()).isEqualTo(200);
        SavedFood unchanged = savedFoodRepository.findById(savedFood.getId()).orElseThrow();
        assertThat(unchanged.getProteinGrams()).isNull();
        assertThat(unchanged.getFiberGrams()).isNull();
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
    void editFoodEntryLoadsValuesAndUpdatesFromSnapshotAfterSavedFoodChangesAndDeactivation()
            throws IOException, InterruptedException {
        SavedFood savedFood = saveSavedFood("Bread", "Bakery", true);
        post("/food", foodEntryForm(savedFood.getId(), "1.50", "slice", LocalDateTime.now().minusHours(2)));
        FoodEntry entry = foodEntryRepository.findAll().getFirst();

        HttpResponse<String> editResponse = get("/food/" + entry.getId() + "/edit");

        assertThat(editResponse.statusCode()).isEqualTo(200);
        assertThat(editResponse.body()).contains(
                "Edit food entry",
                "value=\"1.50\"",
                "value=\"slice\"",
                "Afternoon snack",
                "Bakery - Bread"
        );

        savedFood.setCalories(new BigDecimal("999.00"));
        savedFood.setProteinGrams(new BigDecimal("99.00"));
        savedFood.setActive(false);
        savedFoodRepository.save(savedFood);

        Map<String, String> update = foodEntryEditForm(
                "80.00",
                "gram",
                "DINNER",
                LocalDateTime.now().minusMinutes(10),
                "Dinner notes"
        );
        HttpClient redirectClient = HttpClient.newBuilder()
                .cookieHandler(cookieManager)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        HttpResponse<String> updateResponse = post(redirectClient, "/food/" + entry.getId(), update);

        assertThat(updateResponse.statusCode()).isBetween(300, 399);
        URI redirectLocation = URI.create(updateResponse.headers().firstValue("Location").orElseThrow());
        assertThat(redirectLocation.getPath()).isEqualTo("/food");
        HttpResponse<String> redirectedResponse = get(redirectClient, "/food");
        assertThat(redirectedResponse.statusCode()).isEqualTo(200);
        assertThat(redirectedResponse.body()).contains("Food entry updated.");
        FoodEntry updated = foodEntryRepository.findById(entry.getId()).orElseThrow();
        assertThat(updated.getSavedFoodId()).isEqualTo(savedFood.getId());
        assertThat(updated.getCalculationMultiplier()).isEqualByComparingTo("2.00000000");
        assertThat(updated.getCalories()).isEqualByComparingTo("200.00");
        assertThat(updated.getProteinGrams()).isEqualByComparingTo("8.00");
        assertThat(updated.getCarbohydrateGrams()).isEqualByComparingTo("40.00");
        assertThat(updated.getFatGrams()).isEqualByComparingTo("2.00");
        assertThat(updated.getFiberGrams()).isEqualByComparingTo("4.00");
        assertThat(updated.getMealType()).isEqualTo(MealType.DINNER);
        assertThat(updated.getNotes()).isEqualTo("Dinner notes");
    }

    @Test
    void editFoodEntryUsesSnapshotsAfterSavedFoodIsDeleted() throws IOException, InterruptedException {
        SavedFood savedFood = saveSavedFood("Bread", "Bakery", true);
        post("/food", foodEntryForm(savedFood.getId(), "1.00", "slice", LocalDateTime.now().minusHours(2)));
        FoodEntry entry = foodEntryRepository.findAll().getFirst();

        savedFoodRepository.deleteById(savedFood.getId());
        savedFoodRepository.flush();

        FoodEntry entryAfterDeletion = foodEntryRepository.findById(entry.getId()).orElseThrow();
        assertThat(entryAfterDeletion.getSavedFoodId()).isNull();
        assertThat(entryAfterDeletion.getSavedFoodReferenceAmount()).isEqualByComparingTo("1.00");
        assertThat(entryAfterDeletion.getSavedFoodReferenceUnit()).isEqualTo("slice");

        HttpResponse<String> response = post("/food/" + entry.getId(), foodEntryEditForm(
                "2.00",
                "slice",
                "LUNCH",
                LocalDateTime.now().minusMinutes(10),
                "After deletion"
        ));

        assertThat(response.body()).contains("Food entry updated.");
        FoodEntry updated = foodEntryRepository.findById(entry.getId()).orElseThrow();
        assertThat(updated.getSavedFoodId()).isNull();
        assertThat(updated.getAmount()).isEqualByComparingTo("2.00");
        assertThat(updated.getCalories()).isEqualByComparingTo("200.00");
        assertThat(updated.getProteinGrams()).isEqualByComparingTo("8.00");
        assertThat(updated.getCarbohydrateGrams()).isEqualByComparingTo("40.00");
        assertThat(updated.getFatGrams()).isEqualByComparingTo("2.00");
        assertThat(updated.getFiberGrams()).isEqualByComparingTo("4.00");
    }

    @Test
    void editFoodEntryValidationPreservesValuesAndLegacyDetailsRemainEditable()
            throws IOException, InterruptedException {
        SavedFood savedFood = saveSavedFood("Bread", "Bakery", true);
        post("/food", foodEntryForm(savedFood.getId(), "1.00", "slice", LocalDateTime.now().minusHours(2)));
        FoodEntry snapshotEntry = foodEntryRepository.findAll().getFirst();

        HttpResponse<String> incompatible = post("/food/" + snapshotEntry.getId(), foodEntryEditForm(
                "2.00",
                "cups",
                "LUNCH",
                LocalDateTime.now().minusMinutes(20),
                "Keep this submitted note"
        ));

        assertThat(incompatible.statusCode()).isEqualTo(200);
        assertThat(incompatible.uri().getPath()).endsWith("/food/" + snapshotEntry.getId());
        assertThat(incompatible.body()).contains(
                "Use the stored reference unit, or a supported weight unit when a reference weight is saved.",
                "value=\"2.00\"",
                "value=\"cups\"",
                "Keep this submitted note"
        );

        FoodEntry legacy = saveLegacyEntry(
                "Legacy soup", "100.00", "5.00", "10.00", "3.00", "1.00", LocalDateTime.now().minusHours(1)
        );
        HttpResponse<String> legacyAmountChange = post("/food/" + legacy.getId(), foodEntryEditForm(
                "2.00", "serving", "DINNER", LocalDateTime.now().minusMinutes(15), "Legacy changed"
        ));
        assertThat(legacyAmountChange.body()).contains(
                "Amount and unit cannot be changed because this legacy entry does not contain a complete nutrition snapshot."
        );

        HttpResponse<String> legacyDetails = post("/food/" + legacy.getId(), foodEntryEditForm(
                "1.00", "serving", "DINNER", LocalDateTime.now().minusMinutes(15), "Legacy changed"
        ));
        assertThat(legacyDetails.body()).contains("Food entry updated.");
        FoodEntry updatedLegacy = foodEntryRepository.findById(legacy.getId()).orElseThrow();
        assertThat(updatedLegacy.getMealType()).isEqualTo(MealType.DINNER);
        assertThat(updatedLegacy.getNotes()).isEqualTo("Legacy changed");
        assertThat(updatedLegacy.getCalories()).isEqualByComparingTo("100.00");
    }

    @Test
    void editFoodEntryReturnsNotFoundForMissingAndForeignProfileEntries()
            throws IOException, InterruptedException {
        assertThat(get("/food/999998/edit").statusCode()).isEqualTo(404);
        assertThat(post("/food/999998", foodEntryEditForm(
                "1.00", "serving", "SNACK", LocalDateTime.now().minusMinutes(5), ""
        )).statusCode()).isEqualTo(404);

        insertForeignFoodEntry(999999L);

        assertThat(get("/food/999999/edit").statusCode()).isEqualTo(404);
        assertThat(post("/food/999999", foodEntryEditForm(
                "1.00", "serving", "SNACK", LocalDateTime.of(2026, 7, 31, 20, 0), ""
        )).statusCode()).isEqualTo(404);
    }

    @Test
    void editFoodEntryBeanValidationPreservesSubmittedValues() throws IOException, InterruptedException {
        SavedFood savedFood = saveSavedFood("Bread", "Bakery", true);
        post("/food", foodEntryForm(savedFood.getId(), "1.00", "slice", LocalDateTime.now().minusHours(2)));
        FoodEntry entry = foodEntryRepository.findAll().getFirst();
        LocalDateTime futureEatenAt = LocalDateTime.now().plusDays(1).withSecond(0).withNano(0);

        HttpResponse<String> response = post("/food/" + entry.getId(), foodEntryEditForm(
                "2.00",
                "grams",
                "LUNCH",
                futureEatenAt,
                "Preserve these notes"
        ));

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.uri().getPath()).endsWith("/food/" + entry.getId());
        assertThat(response.body()).contains(
                "must be a date in the past or in the present",
                "value=\"2.00\"",
                "value=\"grams\"",
                "value=\"LUNCH\" selected=\"selected\"",
                "value=\"" + futureEatenAt + "\"",
                "Preserve these notes"
        );
        FoodEntry unchanged = foodEntryRepository.findById(entry.getId()).orElseThrow();
        assertThat(unchanged.getAmount()).isEqualByComparingTo("1.00");
        assertThat(unchanged.getMealType()).isEqualTo(MealType.SNACK);
    }

    @Test
    void postFoodReturnsFormWhenValidationFails() throws IOException, InterruptedException {
        SavedFood savedFood = saveSavedFood("Soup", "Kitchen", true);
        savedFood.setReferenceWeightGrams(null);
        savedFoodRepository.save(savedFood);
        LocalDateTime futureEatenAt = LocalDateTime.now().plusDays(1).withSecond(0).withNano(0);
        Map<String, String> formValues = foodEntryForm(savedFood.getId(), "0", "g", futureEatenAt);

        HttpResponse<String> response = post("/food", formValues);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("must be greater than 0");
        assertThat(response.body()).contains("must be a date in the past or in the present");
        assertThat(response.body()).contains("value=\"" + futureEatenAt + "\"");
        assertThat(foodEntryRepository.count()).isZero();

        formValues.put("amount", "1.00");
        formValues.put("eatenAt", LocalDateTime.now().minusMinutes(5).withSecond(0).withNano(0).toString());
        HttpResponse<String> incompatibleUnitResponse = post("/food", formValues);

        assertThat(incompatibleUnitResponse.statusCode()).isEqualTo(200);
        assertThat(incompatibleUnitResponse.body()).contains("Use the saved food reference unit, or a supported weight unit when a reference weight is saved.");
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
    void foodHistoryShowsSelectedDateEntriesTotalsAndNavigation() throws IOException, InterruptedException {
        LocalDate today = LocalDate.now();
        LocalDate historyDate = today.minusDays(3);
        saveLegacyEntry("Historical apple", "95.00", "0.50", "25.00", "0.30", "4.00", historyDate.atTime(8, 0));
        saveLegacyEntry("Today banana", "105.00", "1.00", "27.00", "0.40", "3.00", today.atTime(9, 0));

        HttpResponse<String> response = get("/food?date=" + historyDate);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains(
                "Historical apple",
                "95.00",
                "Totals for selected date",
                "Entries for selected date",
                "Previous",
                "Today",
                "Next",
                "value=\"" + historyDate + "\"",
                "value=\"" + historyDate + "T"
        );
        assertThat(response.body()).doesNotContain("Today banana", "105.00");
    }

    @Test
    void invalidFoodHistoryDateFallsBackToTodayWithoutServerError() throws IOException, InterruptedException {
        LocalDate today = LocalDate.now();
        saveLegacyEntry("Today entry", "100.00", "1.00", "2.00", "3.00", "4.00", today.atTime(8, 0));

        HttpResponse<String> response = get("/food?date=not-a-date");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains(
                "The requested date was invalid. Showing today instead.",
                "Today entry",
                "Today’s totals",
                "Today’s entries"
        );
    }

    @Test
    void createAndDeleteHistoricalEntryReturnToItsDate() throws IOException, InterruptedException {
        LocalDate historyDate = LocalDate.now().minusDays(5);
        SavedFood savedFood = saveSavedFood("Bread", "Bakery", true);

        HttpResponse<String> createResponse = post("/food?date=" + historyDate, foodEntryForm(
                savedFood.getId(),
                "1.00",
                "slice",
                historyDate.atTime(12, 30)
        ));

        assertThat(createResponse.statusCode()).isEqualTo(200);
        assertThat(createResponse.uri().getPath()).matches("/food(?:;jsessionid=[^/?;]+)?");
        assertThat(createResponse.uri().getQuery()).isEqualTo("date=" + historyDate);
        assertThat(createResponse.body()).contains("Food entry saved.", "Bread", historyDate.toString());

        FoodEntry entry = foodEntryRepository.findAll().getFirst();
        HttpResponse<String> deleteResponse = post("/food/" + entry.getId() + "/delete", Map.of());

        assertThat(deleteResponse.statusCode()).isEqualTo(200);
        assertThat(deleteResponse.uri().getPath()).matches("/food(?:;jsessionid=[^/?;]+)?");
        assertThat(deleteResponse.uri().getQuery()).isEqualTo("date=" + historyDate);
        assertThat(deleteResponse.body()).contains("Food entry deleted.", "No food entries recorded for this date.");
    }

    @Test
    void createOnDifferentEatenDateThenEditMovesEntryWithoutDuplicate() throws IOException, InterruptedException {
        LocalDate selectedDate = LocalDate.of(2026, 7, 31);
        LocalDate originalEatenDate = LocalDate.of(2026, 7, 30);
        SavedFood savedFood = saveSavedFood("Date move bread", "Bakery", true);
        HttpClient redirectClient = HttpClient.newBuilder()
                .cookieHandler(cookieManager)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();

        HttpResponse<String> createResponse = post(
                redirectClient,
                "/food?date=" + selectedDate,
                foodEntryForm(savedFood.getId(), "1.00", "slice", originalEatenDate.atTime(20, 0))
        );

        assertThat(createResponse.statusCode()).isBetween(300, 399);
        URI createLocation = URI.create(createResponse.headers().firstValue("Location").orElseThrow());
        assertThat(createLocation.getPath()).matches("/food(?:;jsessionid=[^/?;]+)?");
        assertThat(createLocation.getQuery()).isEqualTo("date=" + originalEatenDate);
        assertThat(get("/food?date=" + originalEatenDate).body()).contains("Date move bread", "Afternoon snack");
        assertThat(get("/food?date=" + selectedDate).body()).doesNotContain("Afternoon snack");

        FoodEntry entry = foodEntryRepository.findAll().getFirst();
        HttpResponse<String> updateResponse = post(
                redirectClient,
                "/food/" + entry.getId(),
                foodEntryEditForm("1.00", "slice", "DINNER", selectedDate.atTime(20, 0), "Moved")
        );

        assertThat(updateResponse.statusCode()).isBetween(300, 399);
        URI updateLocation = URI.create(updateResponse.headers().firstValue("Location").orElseThrow());
        assertThat(updateLocation.getPath()).matches("/food(?:;jsessionid=[^/?;]+)?");
        assertThat(updateLocation.getQuery()).isEqualTo("date=" + selectedDate);
        assertThat(get("/food?date=" + originalEatenDate).body()).doesNotContain("Moved");
        assertThat(get("/food?date=" + selectedDate).body()).contains("Date move bread", "Moved");
        assertThat(foodEntryRepository.count()).isEqualTo(1);
        assertThat(foodEntryRepository.findById(entry.getId()).orElseThrow().getEatenAt())
                .isEqualTo(selectedDate.atTime(20, 0));
    }

    @Test
    void repositoryDateFilteringHandlesMonthAndYearBoundaries() {
        Long profileId = profileService.getProfile().orElseThrow().id();
        saveLegacyEntry("July end", "100.00", "1.00", "1.00", "1.00", "1.00",
                LocalDateTime.of(2026, 7, 31, 23, 59));
        saveLegacyEntry("August start", "100.00", "1.00", "1.00", "1.00", "1.00",
                LocalDateTime.of(2026, 8, 1, 0, 0));
        saveLegacyEntry("Year end", "100.00", "1.00", "1.00", "1.00", "1.00",
                LocalDateTime.of(2026, 12, 31, 23, 59));
        saveLegacyEntry("New year", "100.00", "1.00", "1.00", "1.00", "1.00",
                LocalDateTime.of(2027, 1, 1, 0, 0));

        assertThat(entriesForDate(profileId, LocalDate.of(2026, 7, 31)))
                .extracting(FoodEntry::getFoodName).containsExactly("July end");
        assertThat(entriesForDate(profileId, LocalDate.of(2026, 8, 1)))
                .extracting(FoodEntry::getFoodName).containsExactly("August start");
        assertThat(entriesForDate(profileId, LocalDate.of(2026, 12, 31)))
                .extracting(FoodEntry::getFoodName).containsExactly("Year end");
        assertThat(entriesForDate(profileId, LocalDate.of(2027, 1, 1)))
                .extracting(FoodEntry::getFoodName).containsExactly("New year");
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

    private List<FoodEntry> entriesForDate(Long profileId, LocalDate date) {
        return foodEntryRepository.findByProfileIdAndEatenAtGreaterThanEqualAndEatenAtLessThanOrderByEatenAtDescIdDesc(
                profileId,
                date.atStartOfDay(),
                date.plusDays(1).atStartOfDay()
        );
    }

    private int countOccurrences(String value, String search) {
        return value.split(Pattern.quote(search), -1).length - 1;
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

    private void insertForeignFoodEntry(Long id) {
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS=0");
        try {
            jdbcTemplate.update("""
                    INSERT INTO food_entries (
                        id, profile_id, food_name, amount, unit, calories, protein_grams,
                        carbohydrate_grams, fat_grams, fiber_grams, meal_type, eaten_at, created_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    id,
                    999999L,
                    "Foreign entry",
                    new BigDecimal("1.00"),
                    "serving",
                    new BigDecimal("100.00"),
                    new BigDecimal("5.00"),
                    new BigDecimal("10.00"),
                    new BigDecimal("3.00"),
                    new BigDecimal("1.00"),
                    "SNACK",
                    LocalDateTime.now().minusMinutes(5),
                    LocalDateTime.now()
            );
        } finally {
            jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS=1");
        }
    }

    private void insertForeignRecentFood(Long savedFoodId, Long entryId, LocalDateTime eatenAt) {
        insertForeignSavedFood(savedFoodId);
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS=0");
        try {
            jdbcTemplate.update("""
                    INSERT INTO food_entries (
                        id, profile_id, saved_food_id, food_name, amount, unit, calories,
                        meal_type, eaten_at, created_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    entryId,
                    999999L,
                    savedFoodId,
                    "Foreign recent food",
                    new BigDecimal("1.00"),
                    "slice",
                    new BigDecimal("100.00"),
                    "SNACK",
                    eatenAt,
                    eatenAt
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

    private String importId(String responseBody) {
        var matcher = Pattern.compile("Import ID: ([0-9a-f-]{36})").matcher(responseBody);
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
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

    private Map<String, String> foodEntryEditForm(
            String amount,
            String unit,
            String mealType,
            LocalDateTime eatenAt,
            String notes
    ) {
        Map<String, String> formValues = new LinkedHashMap<>();
        formValues.put("amount", amount);
        formValues.put("unit", unit);
        formValues.put("mealType", mealType);
        formValues.put("eatenAt", eatenAt.withSecond(0).withNano(0).toString());
        formValues.put("notes", notes);
        return formValues;
    }

    private HttpResponse<String> get(String path) throws IOException, InterruptedException {
        return get(httpClient, path);
    }

    private HttpResponse<String> get(HttpClient client, String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri(path)).GET().build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, Map<String, String> formValues)
            throws IOException, InterruptedException {
        return post(httpClient, path, formValues);
    }

    private HttpResponse<String> post(HttpClient client, String path, Map<String, String> formValues)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri(path))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(formBody(formValues)))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> postMultipartNutritionPhoto() throws IOException, InterruptedException {
        String boundary = "DietAssistantBoundary";
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        body.write("Content-Disposition: form-data; name=\"nutritionFactsPhoto\"; filename=\"facts.jpg\"\r\n".getBytes(StandardCharsets.UTF_8));
        body.write("Content-Type: image/jpeg\r\n\r\n".getBytes(StandardCharsets.UTF_8));
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, 0x4b7bec);
        ImageIO.write(image, "jpeg", body);
        body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        HttpRequest request = HttpRequest.newBuilder(uri("/foods/import"))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
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

    private static Path createTestPendingRoot() {
        try {
            return Files.createTempDirectory("diet-assistant-food-import-test-");
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to create pending-import test directory.", exception);
        }
    }
}

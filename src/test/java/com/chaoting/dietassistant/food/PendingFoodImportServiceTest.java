package com.chaoting.dietassistant.food;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.validation.BeanPropertyBindingResult;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PendingFoodImportServiceTest {

    private byte[] JPEG;
    private byte[] PNG;
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-02T16:00:00Z"), ZoneOffset.UTC);

    @TempDir
    Path temporaryDirectory;

    private PendingFoodImportService service;

    @BeforeEach
    void setUp() throws Exception {
        JPEG = image("jpeg");
        PNG = image("png");
        service = new PendingFoodImportService(temporaryDirectory, 10, 24, new JsonMapper(), CLOCK);
    }

    @Test
    void validatesRequiredNutritionPhotoAndOptionalFrontPhoto() {
        SavedFoodPhotoUploadRequest request = new SavedFoodPhotoUploadRequest();
        BeanPropertyBindingResult errors = errors(request);

        assertThat(service.validateUpload(request, errors)).isFalse();
        assertThat(errors.getFieldError("nutritionFactsPhoto")).isNotNull();
        assertThat(errors.getFieldError("frontPhoto")).isNull();
    }

    @Test
    void rejectsEmptyOversizedUnsupportedAndSpoofedPhotos() {
        assertInvalid(photo("facts.jpg", "image/jpeg", new byte[0]), "photo.empty");
        assertInvalid(photo("facts.jpg", "image/jpeg", new byte[11]), "photo.tooLarge");
        assertInvalid(photo("facts.gif", "image/gif", JPEG), "photo.invalid");
        assertInvalid(photo("facts.jpg", "text/plain", JPEG), "photo.invalid");
        assertInvalid(photo("facts.jpg", "image/jpeg", PNG), "photo.invalid");
    }

    @Test
    void createsIsolatedPendingDirectoryWithSafeNamesAndPendingMetadata() throws Exception {
        SavedFoodPhotoUploadRequest request = request(
                photo("My Product.JPG", "image/jpeg", JPEG),
                photo("nutrition.png", "image/png", PNG)
        );

        PendingFoodImportView view = service.create(request);
        Path importDirectory = service.importDirectory(view.importId());

        assertThat(view.status()).isEqualTo("pending");
        assertThat(view.hasFrontPhoto()).isTrue();
        assertThat(importDirectory).isDirectory();
        assertThat(importDirectory.resolve("front.jpg")).exists();
        assertThat(importDirectory.resolve("front-original.jpg")).exists();
        assertThat(importDirectory.resolve("nutrition-original.png")).exists();
        assertThat(importDirectory.resolve("nutrition.jpg")).exists();
        assertThat(importDirectory.resolve("metadata.json")).content()
                .contains("\"status\" : \"pending\"")
                .contains("\"originalFormat\" : \"png\"")
                .contains("\"preparedFile\" : \"nutrition.jpg\"")
                .doesNotContain("My Product.JPG");
    }

    @Test
    void loadsStructuredResultWithoutReplacingMissingValuesWithZero() throws Exception {
        PendingFoodImportView pending = service.create(request(null, photo("facts.jpg", "image/jpeg", JPEG)));
        Files.writeString(service.importDirectory(pending.importId()).resolve("result.json"), """
                {
                  "name": "Greek yogurt",
                  "brand": null,
                  "referenceAmount": 1.00,
                  "referenceUnit": "cup",
                  "referenceWeightGrams": 227.00,
                  "calories": 100.00,
                  "proteinGrams": 10.00,
                  "carbohydrateGrams": null,
                  "fatGrams": 2.00,
                  "fiberGrams": null,
                  "notes": null
                }
                """);

        SavedFoodRequest request = service.loadResult(pending.importId()).orElseThrow();

        assertThat(request.getName()).isEqualTo("Greek yogurt");
        assertThat(request.getCalories()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(request.getCarbohydrateGrams()).isNull();
        assertThat(request.getFiberGrams()).isNull();
        assertThat(request.getCarbohydrateGrams()).isNull();
        assertThat(request.getFiberGrams()).isNull();
        assertThat(service.find(pending.importId()).orElseThrow().status()).isEqualTo("ready_for_review");
    }

    @Test
    void invalidResultLeavesImportPendingAndAllowsManualCompletion() throws Exception {
        PendingFoodImportView pending = service.create(request(null, photo("facts.jpg", "image/jpeg", JPEG)));
        Files.writeString(service.importDirectory(pending.importId()).resolve("result.json"), "{\"calories\": -1}");

        PendingFoodImportView view = service.find(pending.importId()).orElseThrow();

        assertThat(view.resultAvailable()).isFalse();
        assertThat(view.resultError()).contains("complete the form manually");
        assertThat(service.loadResult(pending.importId())).isEmpty();
    }

    @Test
    void deleteRemovesPhotosMetadataAndResultTogether() throws Exception {
        PendingFoodImportView pending = service.create(request(null, photo("facts.jpg", "image/jpeg", JPEG)));
        Path importDirectory = service.importDirectory(pending.importId());
        Files.writeString(importDirectory.resolve("result.json"), "{}");

        assertThat(service.delete(pending.importId())).isTrue();
        assertThat(importDirectory).doesNotExist();
    }

    @Test
    void cleanupDeletesExpiredImports() {
        PendingFoodImportService immediatelyExpired = new PendingFoodImportService(
                temporaryDirectory,
                10,
                -1,
                new JsonMapper(),
                CLOCK
        );
        PendingFoodImportView pending = immediatelyExpired.create(request(null, photo("facts.jpg", "image/jpeg", JPEG)));

        assertThat(immediatelyExpired.cleanupExpired()).isEqualTo(1);
        assertThat(immediatelyExpired.importDirectory(pending.importId())).doesNotExist();
    }

    @Test
    void statusReadIsReadOnlyAndReportsExpiredWithoutDeletingImport() throws Exception {
        PendingFoodImportService immediatelyExpired = new PendingFoodImportService(
                temporaryDirectory, 10, -1, new JsonMapper(), CLOCK
        );
        PendingFoodImportView pending = immediatelyExpired.create(
                request(null, photo("facts.jpg", "image/jpeg", JPEG))
        );
        Path directory = immediatelyExpired.importDirectory(pending.importId());
        String metadataBefore = Files.readString(directory.resolve("metadata.json"));

        assertThat(immediatelyExpired.currentStatus(pending.importId())).contains("expired");
        assertThat(directory).exists();
        assertThat(Files.readString(directory.resolve("metadata.json"))).isEqualTo(metadataBefore);
    }

    @Test
    void lookupIsReadOnlyWhenFallbackResultBecomesReviewable() throws Exception {
        PendingFoodImportView pending = service.create(request(null, photo("facts.jpg", "image/jpeg", JPEG)));
        Path metadata = service.importDirectory(pending.importId()).resolve("metadata.json");
        Files.writeString(service.importDirectory(pending.importId()).resolve("result.json"), """
                {
                  "name": "Oats", "brand": null, "referenceAmount": 1, "referenceUnit": "cup",
                  "referenceWeightGrams": null, "calories": 100, "proteinGrams": null,
                  "carbohydrateGrams": null, "fatGrams": null, "fiberGrams": null, "notes": null
                }
                """);
        String before = Files.readString(metadata);

        assertThat(service.find(pending.importId()).orElseThrow().status()).isEqualTo("ready_for_review");
        assertThat(Files.readString(metadata)).isEqualTo(before);
    }

    @Test
    void confirmationClaimRejectsProcessingAndPreventsConcurrentDuplicates() throws Exception {
        PendingFoodImportResult recognized = new PendingFoodImportResult("Oats", null, new BigDecimal("1"), "cup",
                null, null, null, null, null, null, null);
        CountDownLatch recognitionStarted = new CountDownLatch(1);
        CountDownLatch releaseRecognition = new CountDownLatch(1);
        PendingFoodImportService automatic = automaticService(request -> {
            recognitionStarted.countDown();
            try { releaseRecognition.await(); }
            catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new FoodPhotoRecognitionException(
                        FoodPhotoRecognitionException.Reason.EXECUTION_FAILED, "interrupted", exception);
            }
            return recognized;
        });
        PendingFoodImportView processing = automatic.create(request(null, photo("facts.jpg", "image/jpeg", JPEG)));
        recognitionStarted.await();

        assertThat(automatic.confirm(processing.importId(), () -> { throw new AssertionError("must not run"); }))
                .isFalse();
        releaseRecognition.countDown();
        awaitFinished(automatic, processing.importId());

        AtomicInteger creates = new AtomicInteger();
        boolean first = automatic.confirm(processing.importId(), creates::incrementAndGet);
        boolean second = automatic.confirm(processing.importId(), creates::incrementAndGet);

        assertThat(first).isTrue();
        assertThat(second).isFalse();
        assertThat(creates).hasValue(1);
        automatic.stopRecognitionExecutor();
    }

    @Test
    void failedSavedFoodCreationRestoresReviewableImport() throws Exception {
        PendingFoodImportView pending = service.create(request(null, photo("facts.jpg", "image/jpeg", JPEG)));
        Path directory = service.importDirectory(pending.importId());

        assertThatThrownBy(() -> service.confirm(pending.importId(), () -> {
            throw new IllegalStateException("database unavailable");
        })).isInstanceOf(IllegalStateException.class).hasMessage("database unavailable");

        assertThat(directory).exists();
        assertThat(service.currentStatus(pending.importId())).contains("pending");
    }

    @Test
    void rejectsPathTraversalImportIds() {
        assertThatThrownBy(() -> service.delete("../../outside"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid pending import id.");
    }

    @Test
    void conversionFailureRollsBackWholeStagingDirectory() {
        ExternalProcessRunner runner = new ExternalProcessRunner();
        ImageMagickPhotoConverter failing = new ImageMagickPhotoConverter("magick", 1, 100, 100, 10000, 95, runner) {
            @Override void normalize(Path input, Path output) throws java.io.IOException {
                throw new java.io.IOException("synthetic decode failure");
            }
        };
        ImageMagickCapabilityChecker available = new ImageMagickCapabilityChecker("magick", 1, runner) {
            @Override Capabilities capabilities() { return new Capabilities(true, true, null); }
        };
        PendingFoodImportService failingService = new PendingFoodImportService(temporaryDirectory, 10_000, 24,
                new JsonMapper(), CLOCK, new ProductPhotoValidator(), failing, available);

        assertThatThrownBy(() -> failingService.create(request(photo("front.jpg", "image/jpeg", JPEG), photo("facts.png", "image/png", PNG))))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Photo processing failed");
        assertThat(temporaryDirectory).isEmptyDirectory();
    }

    @Test
    void backgroundRecognitionMovesPendingThroughProcessingToReviewWithoutSavingFood() throws Exception {
        PendingFoodImportResult recognized = new PendingFoodImportResult("Oats", null, new BigDecimal("1"), "cup",
                new BigDecimal("40"), new BigDecimal("150"), new BigDecimal("5"), new BigDecimal("27"),
                new BigDecimal("3"), new BigDecimal("4"), null);
        PendingFoodImportService automatic = automaticService(request -> recognized);

        PendingFoodImportView created = automatic.create(request(null, photo("facts.jpg", "image/jpeg", JPEG)));
        PendingFoodImportView ready = awaitFinished(automatic, created.importId());

        assertThat(created.status()).isEqualTo("processing");
        assertThat(ready.status()).isEqualTo("ready_for_review");
        assertThat(ready.resultAvailable()).isTrue();
        assertThat(automatic.loadResult(created.importId()).orElseThrow().getCalories()).isEqualByComparingTo("150");
        assertThat(automatic.importDirectory(created.importId()).resolve("result.json")).exists();
        automatic.stopRecognitionExecutor();
    }

    @Test
    void backgroundRecognitionTimeoutAndInvalidResultRemainManuallyCompletableAndRetryable() throws Exception {
        FoodPhotoRecognizer timeout = request -> {
            throw new FoodPhotoRecognitionException(FoodPhotoRecognitionException.Reason.TIMEOUT, "timeout");
        };
        PendingFoodImportService automatic = automaticService(timeout);
        PendingFoodImportView created = automatic.create(request(null, photo("facts.jpg", "image/jpeg", JPEG)));

        PendingFoodImportView failed = awaitFinished(automatic, created.importId());

        assertThat(failed.status()).isEqualTo("recognition_timed_out");
        assertThat(failed.resultAvailable()).isFalse();
        assertThat(failed.resultError()).contains("complete the form manually");
        assertThat(automatic.loadResult(created.importId())).isEmpty();
        assertThat(automatic.retryRecognition(created.importId())).isTrue();
        assertThat(awaitFinished(automatic, created.importId()).status()).isEqualTo("recognition_timed_out");
        automatic.stopRecognitionExecutor();
    }

    private PendingFoodImportService automaticService(FoodPhotoRecognizer recognizer) {
        ExternalProcessRunner runner = new ExternalProcessRunner();
        ImageMagickCapabilityChecker available = new ImageMagickCapabilityChecker("magick", 1, runner) {
            @Override Capabilities capabilities() { return new Capabilities(true, true, null); }
        };
        return new PendingFoodImportService(temporaryDirectory, 10_000, 24, new JsonMapper(), CLOCK,
                new ProductPhotoValidator(), new ImageMagickPhotoConverter("magick", 30, 12000, 12000, 40_000_000, 95, runner),
                available, recognizer, true);
    }

    private PendingFoodImportView awaitFinished(PendingFoodImportService pendingService, String importId) throws Exception {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
        PendingFoodImportView view;
        do {
            view = pendingService.find(importId).orElseThrow();
            if (!"pending".equals(view.status()) && !"processing".equals(view.status())) return view;
            Thread.sleep(20);
        } while (System.nanoTime() < deadline);
        throw new AssertionError("Recognition did not finish: " + view.status());
    }

    private void assertInvalid(MockMultipartFile nutritionPhoto, String expectedCode) {
        SavedFoodPhotoUploadRequest request = request(null, nutritionPhoto);
        BeanPropertyBindingResult errors = errors(request);

        assertThat(service.validateUpload(request, errors)).isFalse();
        assertThat(errors.getFieldErrors("nutritionFactsPhoto"))
                .extracting(error -> error.getCode())
                .contains(expectedCode);
    }

    private SavedFoodPhotoUploadRequest request(MockMultipartFile front, MockMultipartFile nutrition) {
        SavedFoodPhotoUploadRequest request = new SavedFoodPhotoUploadRequest();
        request.setFrontPhoto(front);
        request.setNutritionFactsPhoto(nutrition);
        return request;
    }

    private MockMultipartFile photo(String filename, String contentType, byte[] contents) {
        return new MockMultipartFile("photo", filename, contentType, contents);
    }

    private BeanPropertyBindingResult errors(SavedFoodPhotoUploadRequest request) {
        return new BeanPropertyBindingResult(request, "photoUploadRequest");
    }

    private byte[] image(String format) throws Exception {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, 0x4b7bec);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, format, output);
        return output.toByteArray();
    }
}

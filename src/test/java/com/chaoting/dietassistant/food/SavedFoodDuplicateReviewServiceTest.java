package com.chaoting.dietassistant.food;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.ui.ConcurrentModel;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import tools.jackson.databind.json.JsonMapper;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SavedFoodDuplicateReviewServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-02T16:00:00Z");

    @TempDir
    Path temporaryDirectory;

    private MutableClock clock;
    private PendingFoodImportService pendingImports;
    private SavedFoodService savedFoods;
    private SavedFoodDuplicateReviewService reviews;
    private SavedFoodResponse candidate;
    private byte[] jpeg;

    @BeforeEach
    void setUp() throws Exception {
        clock = new MutableClock(NOW);
        jpeg = syntheticJpeg();
        ImageMagickPhotoConverter converter = new ImageMagickPhotoConverter(
                "unused", 1, 100, 100, 10_000, 95, mock(ExternalProcessRunner.class)) {
            @Override void normalize(Path input, Path output) throws IOException {
                Files.copy(input, output);
            }
        };
        ImageMagickCapabilityChecker checker = new ImageMagickCapabilityChecker(
                "unused", 1, mock(ExternalProcessRunner.class)) {
            @Override Capabilities capabilities() { return new Capabilities(true, true, null); }
        };
        pendingImports = new PendingFoodImportService(temporaryDirectory, 10_000, 24,
                new JsonMapper(), clock, new ProductPhotoValidator(), converter, checker);
        savedFoods = mock(SavedFoodService.class);
        candidate = candidate();
        when(savedFoods.findActiveDuplicates(any())).thenReturn(List.of(candidate));
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        reviews = new SavedFoodDuplicateReviewService(savedFoods, pendingImports, validator, clock);
    }

    @Test
    void concurrentUseExistingAndCreateAnywayHaveOneTerminalWinner() throws Exception {
        PendingFoodImportView pending = pendingImport();
        String token = reviews.beginPhoto(pending.importId(), request()).orElseThrow();
        AtomicInteger creates = countCreates();

        List<SavedFoodDuplicateReviewService.Outcome> outcomes = race(
                () -> reviews.useExisting(token, candidate.id()),
                () -> reviews.createAnyway(token));

        assertThat(outcomes).containsAnyOf(
                SavedFoodDuplicateReviewService.Outcome.USED_EXISTING,
                SavedFoodDuplicateReviewService.Outcome.CREATED);
        assertThat(outcomes).contains(SavedFoodDuplicateReviewService.Outcome.MISSING);
        assertThat(outcomes.stream().filter(this::terminal).count()).isEqualTo(1);
        assertThat(creates).hasValueBetween(0, 1);
        assertTerminalAndConsumed(pending, token);
        verify(savedFoods, never()).update(any(), any());
    }

    @Test
    void concurrentCreateAnywaySubmissionsCreateAtMostOnce() throws Exception {
        PendingFoodImportView pending = pendingImport();
        String token = reviews.beginPhoto(pending.importId(), request()).orElseThrow();
        AtomicInteger creates = countCreates();

        List<SavedFoodDuplicateReviewService.Outcome> outcomes = race(
                () -> reviews.createAnyway(token), () -> reviews.createAnyway(token));

        assertThat(outcomes).containsExactlyInAnyOrder(
                SavedFoodDuplicateReviewService.Outcome.CREATED,
                SavedFoodDuplicateReviewService.Outcome.MISSING);
        assertTerminalAndConsumed(pending, token);
        assertThat(creates).hasValue(1);
    }

    @Test
    void concurrentUseExistingSubmissionsCompleteOnlyOnceWithoutCreating() throws Exception {
        PendingFoodImportView pending = pendingImport();
        String token = reviews.beginPhoto(pending.importId(), request()).orElseThrow();

        List<SavedFoodDuplicateReviewService.Outcome> outcomes = race(
                () -> reviews.useExisting(token, candidate.id()),
                () -> reviews.useExisting(token, candidate.id()));

        assertThat(outcomes).containsExactlyInAnyOrder(
                SavedFoodDuplicateReviewService.Outcome.USED_EXISTING,
                SavedFoodDuplicateReviewService.Outcome.MISSING);
        assertTerminalAndConsumed(pending, token);
        verify(savedFoods, never()).create(any());
        verify(savedFoods, never()).update(any(), any());
    }

    @Test
    void createCallbackFailureRollsBackImportAndAllowsOneSafeRetry() throws Exception {
        PendingFoodImportView pending = pendingImport();
        String token = reviews.beginPhoto(pending.importId(), request()).orElseThrow();
        AtomicInteger attempts = new AtomicInteger();
        doAnswer(invocation -> {
            if (attempts.getAndIncrement() == 0) throw new IllegalStateException("database unavailable");
            return candidate;
        }).when(savedFoods).create(any());

        assertThatThrownBy(() -> reviews.createAnyway(token))
                .isInstanceOf(IllegalStateException.class).hasMessage("database unavailable");
        assertThat(pendingImports.currentStatus(pending.importId())).contains("pending");
        assertThat(reviews.find(token)).isPresent();

        assertThat(reviews.createAnyway(token)).isEqualTo(SavedFoodDuplicateReviewService.Outcome.CREATED);
        assertThat(attempts).hasValue(2);
        assertTerminalAndConsumed(pending, token);
    }

    @Test
    void confirmFalseCannotCreateOrUseAndCannotChangeTerminalImport() throws Exception {
        PendingFoodImportView createPending = pendingImport();
        String createToken = reviews.beginPhoto(createPending.importId(), request()).orElseThrow();
        deleteTree(createPendingDirectory(createPending));

        assertThat(reviews.createAnyway(createToken)).isEqualTo(SavedFoodDuplicateReviewService.Outcome.MISSING);
        assertThat(reviews.createAnyway(createToken)).isEqualTo(SavedFoodDuplicateReviewService.Outcome.MISSING);
        verify(savedFoods, never()).create(any());

        PendingFoodImportView usePending = pendingImport();
        String useToken = reviews.beginPhoto(usePending.importId(), request()).orElseThrow();
        deleteTree(createPendingDirectory(usePending));
        assertThat(reviews.useExisting(useToken, candidate.id())).isEqualTo(SavedFoodDuplicateReviewService.Outcome.MISSING);
        assertThat(reviews.useExisting(useToken, candidate.id())).isEqualTo(SavedFoodDuplicateReviewService.Outcome.MISSING);
        verify(savedFoods, never()).update(any(), any());
        assertThat(pendingImports.currentStatus(createPending.importId())).isEmpty();
        assertThat(pendingImports.currentStatus(usePending.importId())).isEmpty();
    }

    @Test
    void createFinalizationFailureLeavesNoSecondCreationOpportunity() throws Exception {
        PendingFoodImportView pending = pendingImport();
        Path metadata = createPendingDirectory(pending).resolve("metadata.json");
        String token = reviews.beginPhoto(pending.importId(), request()).orElseThrow();
        AtomicInteger creates = new AtomicInteger();
        doAnswer(invocation -> {
            creates.incrementAndGet();
            Files.delete(metadata);
            Files.createDirectory(metadata);
            return candidate;
        }).when(savedFoods).create(any());

        assertThatThrownBy(() -> reviews.createAnyway(token)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("confirmation status could not be finalized");
        assertThat(creates).hasValue(1);
        assertThat(pendingImports.confirm(pending.importId(), creates::incrementAndGet)).isFalse();
        assertThat(reviews.createAnyway(token)).isEqualTo(SavedFoodDuplicateReviewService.Outcome.MISSING);
        assertThat(creates).hasValue(1);
    }

    @Test
    void useExistingFinalizationFailureCannotChangeOutcomeOrCandidate() throws Exception {
        PendingFoodImportView pending = pendingImport();
        Path metadata = createPendingDirectory(pending).resolve("metadata.json");
        String token = reviews.beginPhoto(pending.importId(), request()).orElseThrow();
        AtomicInteger duplicateReads = new AtomicInteger();
        when(savedFoods.findActiveDuplicates(any())).thenAnswer(invocation -> {
            if (duplicateReads.incrementAndGet() == 2) {
                Files.delete(metadata);
                Files.createDirectory(metadata);
            }
            return List.of(candidate);
        });

        assertThatThrownBy(() -> reviews.useExisting(token, candidate.id())).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("confirmation status could not be finalized");
        assertThat(pendingImports.confirm(pending.importId(), () -> { throw new AssertionError("must not run"); })).isFalse();
        assertThat(reviews.useExisting(token, candidate.id())).isEqualTo(SavedFoodDuplicateReviewService.Outcome.MISSING);
        verify(savedFoods, never()).create(any());
        verify(savedFoods, never()).update(any(), any());
    }

    @Test
    void manualAndPhotoReviewGetAreReadOnlyAndLeaveTokensUsable() throws Exception {
        SavedFoodRequest proposed = request();
        String manualToken = reviews.beginManual(proposed).orElseThrow();
        var manualBefore = reviews.find(manualToken).orElseThrow();
        SavedFoodDuplicateReviewController controller = new SavedFoodDuplicateReviewController(reviews);

        assertThat(controller.review(manualToken, new ConcurrentModel(), new RedirectAttributesModelMap()))
                .isEqualTo("food-duplicate-review");
        var manualAfter = reviews.find(manualToken).orElseThrow();
        assertThat(manualAfter.source()).isEqualTo(manualBefore.source());
        assertThat(manualAfter.importId()).isEqualTo(manualBefore.importId());
        assertThat(manualAfter.requested().getNotes()).isEqualTo(manualBefore.requested().getNotes());
        assertThat(manualAfter.candidates()).isEqualTo(manualBefore.candidates());
        assertThat(reviews.useExisting(manualToken, candidate.id()))
                .isEqualTo(SavedFoodDuplicateReviewService.Outcome.USED_EXISTING);

        PendingFoodImportView pending = pendingImport();
        String photoToken = reviews.beginPhoto(pending.importId(), proposed).orElseThrow();
        Path directory = createPendingDirectory(pending);
        Map<String, String> before = fileHashes(directory);
        String metadataBefore = Files.readString(directory.resolve("metadata.json"));

        assertThat(controller.review(photoToken, new ConcurrentModel(), new RedirectAttributesModelMap()))
                .isEqualTo("food-duplicate-review");
        assertThat(fileHashes(directory)).isEqualTo(before);
        assertThat(Files.readString(directory.resolve("metadata.json"))).isEqualTo(metadataBefore);
        assertThat(pendingImports.currentStatus(pending.importId())).contains("pending");
        assertThat(reviews.useExisting(photoToken, candidate.id()))
                .isEqualTo(SavedFoodDuplicateReviewService.Outcome.USED_EXISTING);
    }

    @Test
    void unknownExpiredAndConsumedTokensFailWithoutSideEffects() throws Exception {
        SavedFoodDuplicateReviewController controller = new SavedFoodDuplicateReviewController(reviews);
        assertThat(controller.review("unknown", new ConcurrentModel(), new RedirectAttributesModelMap()))
                .isEqualTo("redirect:/foods");
        assertThat(reviews.createAnyway("unknown")).isEqualTo(SavedFoodDuplicateReviewService.Outcome.MISSING);
        assertThat(reviews.useExisting("unknown", candidate.id())).isEqualTo(SavedFoodDuplicateReviewService.Outcome.MISSING);

        PendingFoodImportView expiredPending = pendingImport();
        String expired = reviews.beginPhoto(expiredPending.importId(), request()).orElseThrow();
        String metadataBefore = Files.readString(createPendingDirectory(expiredPending).resolve("metadata.json"));
        clock.advanceSeconds(TimeUnit.MINUTES.toSeconds(31));
        assertThat(controller.review(expired, new ConcurrentModel(), new RedirectAttributesModelMap()))
                .isEqualTo("redirect:/foods");
        assertThat(reviews.createAnyway(expired)).isEqualTo(SavedFoodDuplicateReviewService.Outcome.MISSING);
        assertThat(reviews.useExisting(expired, candidate.id())).isEqualTo(SavedFoodDuplicateReviewService.Outcome.MISSING);
        assertThat(Files.readString(createPendingDirectory(expiredPending).resolve("metadata.json"))).isEqualTo(metadataBefore);

        clock.setInstant(NOW);
        PendingFoodImportView consumedPending = pendingImport();
        String consumed = reviews.beginPhoto(consumedPending.importId(), request()).orElseThrow();
        assertThat(reviews.useExisting(consumed, candidate.id()))
                .isEqualTo(SavedFoodDuplicateReviewService.Outcome.USED_EXISTING);
        assertThat(reviews.find(consumed)).isEmpty();
        assertThat(reviews.createAnyway(consumed)).isEqualTo(SavedFoodDuplicateReviewService.Outcome.MISSING);
        assertThat(reviews.useExisting(consumed, candidate.id())).isEqualTo(SavedFoodDuplicateReviewService.Outcome.MISSING);
        verify(savedFoods, never()).create(any());
        verify(savedFoods, never()).update(any(), any());
    }

    private List<SavedFoodDuplicateReviewService.Outcome> race(Action first, Action second) throws Exception {
        CyclicBarrier start = new CyclicBarrier(3);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<SavedFoodDuplicateReviewService.Outcome> one = executor.submit(() -> { start.await(); return first.run(); });
            Future<SavedFoodDuplicateReviewService.Outcome> two = executor.submit(() -> { start.await(); return second.run(); });
            start.await(5, TimeUnit.SECONDS);
            List<SavedFoodDuplicateReviewService.Outcome> outcomes = new ArrayList<>();
            outcomes.add(one.get(5, TimeUnit.SECONDS));
            outcomes.add(two.get(5, TimeUnit.SECONDS));
            return outcomes;
        }
    }

    private void assertTerminalAndConsumed(PendingFoodImportView pending, String token) {
        assertThat(createPendingDirectory(pending)).doesNotExist();
        assertThat(pendingImports.confirm(pending.importId(), () -> { throw new AssertionError("must not run"); })).isFalse();
        assertThat(reviews.find(token)).isEmpty();
        assertThat(reviews.createAnyway(token)).isEqualTo(SavedFoodDuplicateReviewService.Outcome.MISSING);
    }

    private AtomicInteger countCreates() {
        AtomicInteger creates = new AtomicInteger();
        doAnswer(invocation -> { creates.incrementAndGet(); return candidate; }).when(savedFoods).create(any());
        return creates;
    }

    private boolean terminal(SavedFoodDuplicateReviewService.Outcome outcome) {
        return outcome == SavedFoodDuplicateReviewService.Outcome.CREATED
                || outcome == SavedFoodDuplicateReviewService.Outcome.USED_EXISTING;
    }

    private PendingFoodImportView pendingImport() {
        SavedFoodPhotoUploadRequest upload = new SavedFoodPhotoUploadRequest();
        upload.setNutritionFactsPhoto(new MockMultipartFile("nutritionFactsPhoto", "facts.jpg", "image/jpeg", jpeg));
        return pendingImports.create(upload);
    }

    private Path createPendingDirectory(PendingFoodImportView pending) {
        return pendingImports.importDirectory(pending.importId());
    }

    private SavedFoodRequest request() {
        SavedFoodRequest request = new SavedFoodRequest();
        request.setName("Diet Coke");
        request.setBrand("Coca-Cola");
        request.setReferenceAmount(BigDecimal.ONE);
        request.setReferenceUnit("can");
        request.setCalories(BigDecimal.ZERO);
        request.setNotes("server-held notes");
        return request;
    }

    private SavedFoodResponse candidate() {
        return new SavedFoodResponse(42L, "Diet Coke", "Coca-Cola", BigDecimal.ONE, "can", null,
                BigDecimal.ZERO, null, null, null, null, "existing notes", true,
                LocalDateTime.of(2026, 1, 1, 0, 0), LocalDateTime.of(2026, 1, 1, 0, 0));
    }

    private byte[] syntheticJpeg() throws IOException {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, 0x123456);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "jpeg", output);
        return output.toByteArray();
    }

    private Map<String, String> fileHashes(Path directory) throws Exception {
        try (var files = Files.list(directory)) {
            var result = new java.util.TreeMap<String, String>();
            for (Path file : files.toList()) {
                result.put(file.getFileName().toString(), HexFormat.of().formatHex(
                        MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file))));
            }
            return result;
        }
    }

    private void deleteTree(Path directory) throws IOException {
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }

    @FunctionalInterface
    private interface Action {
        SavedFoodDuplicateReviewService.Outcome run() throws Exception;
    }

    private static final class MutableClock extends Clock {
        private Instant instant;
        private MutableClock(Instant instant) { this.instant = instant; }
        void advanceSeconds(long seconds) { instant = instant.plusSeconds(seconds); }
        void setInstant(Instant instant) { this.instant = instant; }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
    }
}

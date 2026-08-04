package com.chaoting.dietassistant.food;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.validation.BindingResult;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.PostConstruct;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

@Service
public class PendingFoodImportService {

    private static final Logger logger = LoggerFactory.getLogger(PendingFoodImportService.class);

    static final String METADATA_FILE = "metadata.json";
    static final String RESULT_FILE = "result.json";
    private static final Pattern IMPORT_ID_PATTERN = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}");

    private final Path pendingRoot;
    private final long maxFileSizeBytes;
    private final long expirationHours;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final ProductPhotoValidator validator;
    private final ImageMagickPhotoConverter converter;
    private final ImageMagickCapabilityChecker capabilityChecker;
    private final FoodPhotoRecognizer recognizer;
    private final boolean automaticRecognitionEnabled;
    private final ExecutorService recognitionExecutor;

    @Autowired
    public PendingFoodImportService(
            @Value("${diet-assistant.food-import.pending-directory:data/pending-food-imports}") String pendingDirectory,
            @Value("${diet-assistant.food-import.max-file-size-bytes:10485760}") long maxFileSizeBytes,
            @Value("${diet-assistant.food-import.expiration-hours:24}") long expirationHours,
            ObjectMapper objectMapper, Clock clock, ProductPhotoValidator validator,
            ImageMagickPhotoConverter converter, ImageMagickCapabilityChecker capabilityChecker,
            FoodPhotoRecognizer recognizer,
            @Value("${diet-assistant.food-import.recognizer.enabled:true}") boolean automaticRecognitionEnabled
    ) {
        this(Path.of(pendingDirectory), maxFileSizeBytes, expirationHours, objectMapper, clock, validator, converter,
                capabilityChecker, recognizer, automaticRecognitionEnabled);
    }

    PendingFoodImportService(Path root, long maxSize, long expirationHours, ObjectMapper mapper, Clock clock,
                             ProductPhotoValidator validator, ImageMagickPhotoConverter converter,
                             ImageMagickCapabilityChecker capabilityChecker, FoodPhotoRecognizer recognizer,
                             boolean automaticRecognitionEnabled) {
        this.pendingRoot = root.toAbsolutePath().normalize();
        this.maxFileSizeBytes = maxSize;
        this.expirationHours = expirationHours;
        this.objectMapper = mapper;
        this.clock = clock;
        this.validator = validator;
        this.converter = converter;
        this.capabilityChecker = capabilityChecker;
        this.recognizer = recognizer;
        this.automaticRecognitionEnabled = automaticRecognitionEnabled;
        this.recognitionExecutor = Executors.newVirtualThreadPerTaskExecutor();
    }

    PendingFoodImportService(Path root, long maxSize, long expirationHours, ObjectMapper mapper, Clock clock,
                             ProductPhotoValidator validator, ImageMagickPhotoConverter converter,
                             ImageMagickCapabilityChecker capabilityChecker) {
        this(root, maxSize, expirationHours, mapper, clock, validator, converter, capabilityChecker,
                request -> { throw new FoodPhotoRecognitionException(FoodPhotoRecognitionException.Reason.UNAVAILABLE, "disabled"); },
                false);
    }

    PendingFoodImportService(Path root, long maxSize, long expirationHours, ObjectMapper mapper, Clock clock) {
        this(root, maxSize, expirationHours, mapper, clock, new ProductPhotoValidator(),
                new ImageMagickPhotoConverter("magick", 30, 12000, 12000, 40_000_000, 95, new ExternalProcessRunner()),
                new ImageMagickCapabilityChecker("magick", 30, new ExternalProcessRunner()),
                request -> { throw new FoodPhotoRecognitionException(FoodPhotoRecognitionException.Reason.UNAVAILABLE, "disabled"); },
                false);
    }

    ImageMagickCapabilityChecker.Capabilities capabilities() { return capabilityChecker.capabilities(); }

    @PostConstruct
    void resumeInterruptedRecognition() {
        if (!automaticRecognitionEnabled || !Files.isDirectory(pendingRoot)) return;
        try (var directories = Files.list(pendingRoot)) {
            for (Path directory : directories.filter(Files::isDirectory).toList()) {
                String importId = directory.getFileName().toString();
                if (!isValidImportId(importId)) continue;
                Optional<PendingFoodImportMetadata> metadata = readMetadata(importId);
                if (metadata.isEmpty()) continue;
                if ("processing".equals(metadata.orElseThrow().status())) {
                    writeJsonAtomically(directory.resolve(METADATA_FILE), metadata.orElseThrow().retry());
                }
                if ("pending".equals(readMetadata(importId).map(PendingFoodImportMetadata::status).orElse(null))) {
                    startRecognition(importId);
                }
            }
        } catch (IOException exception) {
            // Startup remains available; affected imports can be retried or completed manually.
        }
    }

    public boolean validateUpload(SavedFoodPhotoUploadRequest request, BindingResult errors) {
        var capabilities = capabilities();
        if (!capabilities.normalizationAvailable()) {
            errors.reject("photo.runtime", capabilities.message());
            return false;
        }
        validatePhoto(request.getNutritionFactsPhoto(), "nutritionFactsPhoto", true, capabilities, errors);
        validatePhoto(request.getFrontPhoto(), "frontPhoto", false, capabilities, errors);
        return !errors.hasErrors();
    }

    public PendingFoodImportView create(SavedFoodPhotoUploadRequest request) {
        cleanupExpired();
        String importId = UUID.randomUUID().toString();
        Path staging = pendingRoot.resolve(".staging-" + importId);
        Path published = resolveImportDirectory(importId);
        try {
            Files.createDirectories(pendingRoot);
            Files.createDirectory(staging);
            PendingFoodImportMetadata.PhotoFiles nutrition = stage(request.getNutritionFactsPhoto(), staging, "nutrition");
            PendingFoodImportMetadata.PhotoFiles front = request.getFrontPhoto() == null || request.getFrontPhoto().isEmpty()
                    ? null : stage(request.getFrontPhoto(), staging, "front");
            LocalDateTime created = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
            PendingFoodImportMetadata metadata = new PendingFoodImportMetadata(importId, "pending", created,
                    created.plusHours(expirationHours), nutrition, front, null, null, null);
            writeJson(staging.resolve(METADATA_FILE), metadata);
            publish(staging, published);
            if (automaticRecognitionEnabled) {
                PendingFoodImportMetadata processing = startRecognition(importId);
                return toView(processing, false, null);
            }
            return toView(metadata, false, null);
        } catch (IOException | RuntimeException exception) {
            deleteDirectoryQuietly(staging);
            deleteDirectoryQuietly(published);
            throw new IllegalStateException(userSafeCreateError(exception), exception);
        }
    }

    private PendingFoodImportMetadata.PhotoFiles stage(MultipartFile photo, Path staging, String role) throws IOException {
        ProductPhotoFormat format = validator.validate(photo);
        String originalName = role + "-original." + format.extension;
        String preparedName = role + ".jpg";
        Path original = staging.resolve(originalName);
        try (InputStream input = photo.getInputStream()) { Files.copy(input, original); }
        converter.normalize(original, staging.resolve(preparedName));
        return new PendingFoodImportMetadata.PhotoFiles(format.name().toLowerCase(), originalName, preparedName);
    }

    private void publish(Path staging, Path published) throws IOException {
        try { Files.move(staging, published, StandardCopyOption.ATOMIC_MOVE); }
        catch (AtomicMoveNotSupportedException exception) { throw new IOException("Pending import storage must support atomic directory moves.", exception); }
    }

    public Optional<PendingFoodImportView> find(String importId) {
        return readMetadata(importId)
                .filter(metadata -> !metadata.expiresAt().isBefore(LocalDateTime.now(clock)))
                .map(metadata -> {
            Path result = resolveImportDirectory(importId).resolve(RESULT_FILE);
            if (!Files.isRegularFile(result)) return toView(metadata, false, metadata.recognitionError());
            if ("processing".equals(metadata.status())) return toView(metadata, false, null);
            try {
                readAndValidateResult(result);
                PendingFoodImportMetadata ready = metadata.withStatus("ready_for_review");
                return toView(ready, true, null);
            } catch (RuntimeException exception) {
                return toView(metadata, false, "The Codex result could not be loaded. You can correct result.json or complete the form manually.");
            }
        });
    }

    /** Returns lifecycle state without cleaning up, changing metadata, or scheduling recognition. */
    public Optional<String> currentStatus(String importId) {
        return readMetadata(importId).map(metadata ->
                metadata.expiresAt().isBefore(LocalDateTime.now(clock)) ? "expired" : metadata.status());
    }

    public Optional<SavedFoodRequest> loadResult(String importId) {
        if (readMetadata(importId).isEmpty()) return Optional.empty();
        Path result = resolveImportDirectory(importId).resolve(RESULT_FILE);
        if (!Files.isRegularFile(result)) return Optional.empty();
        try { return Optional.of(toRequest(readAndValidateResult(result))); }
        catch (RuntimeException exception) { return Optional.empty(); }
    }

    public synchronized boolean delete(String importId) {
        Path directory = resolveImportDirectory(importId);
        if (!Files.exists(directory)) return false;
        deleteDirectory(directory);
        return true;
    }

    public synchronized boolean retryRecognition(String importId) {
        if (!automaticRecognitionEnabled) return false;
        Optional<PendingFoodImportMetadata> current = readMetadata(importId);
        if (current.isEmpty() || !("recognition_failed".equals(current.orElseThrow().status())
                || "recognition_timed_out".equals(current.orElseThrow().status()))) return false;
        Path directory = resolveImportDirectory(importId);
        try {
            Files.deleteIfExists(directory.resolve(RESULT_FILE));
            writeJsonAtomically(directory.resolve(METADATA_FILE), current.orElseThrow().retry());
            startRecognition(importId);
            return true;
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to retry automatic recognition.", exception);
        }
    }

    public synchronized boolean confirm(String importId, Runnable createSavedFood) {
        Optional<PendingFoodImportMetadata> current = readMetadata(importId);
        if (current.isEmpty() || current.orElseThrow().expiresAt().isBefore(LocalDateTime.now(clock))
                || !canConfirm(current.orElseThrow().status())) {
            return false;
        }
        Path metadataPath = resolveImportDirectory(importId).resolve(METADATA_FILE);
        PendingFoodImportMetadata original = current.orElseThrow();
        try {
            writeJsonAtomically(metadataPath, original.withStatus("confirming"));
            createSavedFood.run();
        } catch (RuntimeException exception) {
            try { writeJsonAtomically(metadataPath, original); }
            catch (IOException ignored) { }
            throw exception;
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to claim the pending import for confirmation.", exception);
        }
        try {
            writeJsonAtomically(metadataPath, original.withStatus("confirmed"));
        } catch (IOException exception) {
            throw new IllegalStateException("Saved Food was created, but confirmation status could not be finalized.", exception);
        }
        deleteDirectory(resolveImportDirectory(importId));
        return true;
    }

    private boolean canConfirm(String status) {
        return "pending".equals(status) || "ready_for_review".equals(status)
                || "recognition_failed".equals(status) || "recognition_timed_out".equals(status);
    }

    public synchronized int cleanupExpired() {
        if (!Files.isDirectory(pendingRoot)) return 0;
        int deleted = 0;
        try (var directories = Files.list(pendingRoot)) {
            for (Path directory : directories.filter(Files::isDirectory).toList()) {
                String name = directory.getFileName().toString();
                if (name.startsWith(".staging-")) {
                    if (Files.getLastModifiedTime(directory).toInstant().isBefore(clock.instant().minusSeconds(3600))) deleteDirectory(directory);
                    continue;
                }
                if (!isValidImportId(name)) continue;
                Optional<PendingFoodImportMetadata> metadata = readMetadata(name);
                if (metadata.isPresent() && metadata.orElseThrow().expiresAt().isBefore(LocalDateTime.now(clock))) {
                    deleteDirectory(directory); deleted++;
                }
            }
        } catch (IOException exception) { throw new IllegalStateException("Unable to clean expired pending food imports.", exception); }
        return deleted;
    }

    Path importDirectory(String importId) { return resolveImportDirectory(importId); }

    private void validatePhoto(MultipartFile photo, String field, boolean required,
                               ImageMagickCapabilityChecker.Capabilities capabilities, BindingResult errors) {
        if (photo == null || photo.isEmpty()) {
            if (required || photo != null && photo.getOriginalFilename() != null && !photo.getOriginalFilename().isBlank())
                errors.rejectValue(field, "photo.empty", required ? "Nutrition Facts Photo is required." : "The selected photo is empty.");
            return;
        }
        if (photo.getSize() > maxFileSizeBytes) errors.rejectValue(field, "photo.tooLarge", "Photo must be 10 MB or smaller.");
        try {
            ProductPhotoFormat format = validator.validate(photo);
            if ((format == ProductPhotoFormat.HEIC || format == ProductPhotoFormat.HEIF) && !capabilities.heicReadAvailable())
                errors.rejectValue(field, "photo.heicRuntime", capabilities.message());
        } catch (ProductPhotoValidator.InvalidPhotoException exception) {
            errors.rejectValue(field, "photo.invalid", exception.getMessage());
        } catch (IOException exception) { errors.rejectValue(field, "photo.unreadable", "Photo could not be read."); }
    }

    private Optional<PendingFoodImportMetadata> readMetadata(String importId) {
        Path path = resolveImportDirectory(importId).resolve(METADATA_FILE);
        if (!Files.isRegularFile(path)) return Optional.empty();
        try {
            PendingFoodImportMetadata metadata = objectMapper.readValue(path.toFile(), PendingFoodImportMetadata.class);
            return importId.equals(metadata.importId()) ? Optional.of(metadata) : Optional.empty();
        } catch (RuntimeException exception) { return Optional.empty(); }
    }

    private PendingFoodImportResult readAndValidateResult(Path path) {
        PendingFoodImportResult result = objectMapper.readValue(path.toFile(), PendingFoodImportResult.class);
        validateResult(result);
        return result;
    }

    private void requireNonNegative(BigDecimal value, String field) { if (value != null && value.signum() < 0) throw new IllegalArgumentException(field + " must not be negative."); }

    private SavedFoodRequest toRequest(PendingFoodImportResult result) {
        SavedFoodRequest request = new SavedFoodRequest(); request.setName(result.name()); request.setBrand(result.brand());
        request.setReferenceAmount(result.referenceAmount()); request.setReferenceUnit(result.referenceUnit()); request.setReferenceWeightGrams(result.referenceWeightGrams());
        request.setCalories(result.calories()); request.setProteinGrams(result.proteinGrams()); request.setCarbohydrateGrams(result.carbohydrateGrams());
        request.setFatGrams(result.fatGrams()); request.setFiberGrams(result.fiberGrams()); request.setNotes(result.notes()); return request;
    }

    private PendingFoodImportView toView(PendingFoodImportMetadata metadata, boolean available, String error) {
        return new PendingFoodImportView(metadata.importId(), metadata.status(), metadata.createdAt(), metadata.expiresAt(), metadata.front() != null,
                metadata.nutrition().preparedFile(), metadata.front() == null ? null : metadata.front().preparedFile(), available, error);
    }

    private void recognize(String importId) {
        Path directory;
        try {
            directory = resolveImportDirectory(importId);
            Optional<PendingFoodImportMetadata> current = readMetadata(importId);
            if (current.isEmpty() || !"processing".equals(current.orElseThrow().status())) return;
            PendingFoodImportMetadata processing = current.orElseThrow();
            FoodPhotoRecognitionRequest request = new FoodPhotoRecognitionRequest(
                    importId,
                    directory.resolve(processing.nutrition().preparedFile()),
                    Optional.ofNullable(processing.front()).map(photo -> directory.resolve(photo.preparedFile()))
            );
            PendingFoodImportResult recognized = recognizer.recognize(request);
            validateResult(recognized);
            if (!isStillProcessing(importId)) return;
            writeJsonAtomically(directory.resolve(RESULT_FILE), recognized);
            if (!isStillProcessing(importId)) return;
            writeJsonAtomically(directory.resolve(METADATA_FILE), processing.completed("ready_for_review", now(), null));
            logger.info("Food photo import ready for review: importId={}", importId);
        } catch (FoodPhotoRecognitionException exception) {
            logger.warn("Food photo import recognition failed: importId={}, reason={}", importId, exception.reason());
            markRecognitionFailure(importId, exception.reason());
        } catch (RuntimeException | IOException exception) {
            logger.warn("Food photo import recognition produced an invalid result: importId={}, reason={}",
                    importId, exception.getClass().getSimpleName());
            markRecognitionFailure(importId, FoodPhotoRecognitionException.Reason.INVALID_RESULT);
        }
    }

    private PendingFoodImportMetadata startRecognition(String importId) throws IOException {
        PendingFoodImportMetadata pending = readMetadata(importId)
                .filter(metadata -> "pending".equals(metadata.status()))
                .orElseThrow(() -> new IllegalStateException("Pending import is not ready for recognition."));
        PendingFoodImportMetadata processing = pending.processing(now());
        writeJsonAtomically(resolveImportDirectory(importId).resolve(METADATA_FILE), processing);
        recognitionExecutor.submit(() -> recognize(importId));
        return processing;
    }

    private boolean isStillProcessing(String importId) {
        return readMetadata(importId).map(metadata -> "processing".equals(metadata.status())).orElse(false);
    }

    private void markRecognitionFailure(String importId, FoodPhotoRecognitionException.Reason reason) {
        try {
            Optional<PendingFoodImportMetadata> current = readMetadata(importId);
            if (current.isEmpty() || !"processing".equals(current.orElseThrow().status())) return;
            String status = reason == FoodPhotoRecognitionException.Reason.TIMEOUT ? "recognition_timed_out" : "recognition_failed";
            String message = switch (reason) {
                case TIMEOUT -> "Automatic recognition timed out. You can retry later or complete the form manually.";
                case UNAVAILABLE -> "Local Codex is unavailable. You can complete the form manually.";
                case EXECUTION_FAILED -> "Automatic recognition failed. You can complete the form manually.";
                case INVALID_RESULT -> "Automatic recognition returned an invalid result. You can complete the form manually.";
            };
            writeJsonAtomically(resolveImportDirectory(importId).resolve(METADATA_FILE),
                    current.orElseThrow().completed(status, now(), message));
        } catch (IOException | RuntimeException ignored) {
            // Cancellation or expiration may remove the directory while recognition is finishing.
        }
    }

    private void validateResult(PendingFoodImportResult result) {
        requireNonNegative(result.referenceWeightGrams(), "referenceWeightGrams"); requireNonNegative(result.calories(), "calories");
        requireNonNegative(result.proteinGrams(), "proteinGrams"); requireNonNegative(result.carbohydrateGrams(), "carbohydrateGrams");
        requireNonNegative(result.fatGrams(), "fatGrams"); requireNonNegative(result.fiberGrams(), "fiberGrams");
        if (result.referenceAmount() != null && result.referenceAmount().signum() <= 0) throw new IllegalArgumentException("referenceAmount must be positive.");
    }

    private LocalDateTime now() { return LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS); }

    private void writeJsonAtomically(Path path, Object value) throws IOException {
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        writeJson(temporary, value);
        try {
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.deleteIfExists(temporary);
            throw new IOException("Pending import storage must support atomic file moves.", exception);
        }
    }

    @PreDestroy
    void stopRecognitionExecutor() {
        recognitionExecutor.shutdownNow();
    }

    private void writeJson(Path path, Object value) throws IOException { objectMapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), value); }
    private Path resolveImportDirectory(String id) {
        if (!isValidImportId(id)) throw new IllegalArgumentException("Invalid pending import id.");
        Path resolved = pendingRoot.resolve(id).normalize();
        if (!resolved.getParent().equals(pendingRoot)) throw new IllegalArgumentException("Invalid pending import path.");
        return resolved;
    }
    private boolean isValidImportId(String id) { return id != null && IMPORT_ID_PATTERN.matcher(id).matches(); }
    private void deleteDirectory(Path directory) {
        try (var paths = Files.walk(directory)) { for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path); }
        catch (IOException exception) { throw new IllegalStateException("Unable to delete the pending food import.", exception); }
    }
    private void deleteDirectoryQuietly(Path directory) { if (Files.exists(directory)) try { deleteDirectory(directory); } catch (IllegalStateException ignored) { } }
    private String userSafeCreateError(Exception exception) {
        if (exception.getCause() instanceof ExternalProcessRunner.ProcessTimeoutException
                || exception instanceof ExternalProcessRunner.ProcessTimeoutException) {
            return "Photo processing timed out. Try a smaller image or verify the ImageMagick runtime limits.";
        }
        return "Photo processing failed. Verify that the image is valid and that ImageMagick 7 supports its format.";
    }
}

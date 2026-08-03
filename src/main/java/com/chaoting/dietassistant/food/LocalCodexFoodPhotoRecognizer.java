package com.chaoting.dietassistant.food;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Component
class LocalCodexFoodPhotoRecognizer implements FoodPhotoRecognizer {

    private static final Logger logger = LoggerFactory.getLogger(LocalCodexFoodPhotoRecognizer.class);

    private static final String PROMPT = """
            Read the attached prepared product photos. The nutrition photo is authoritative for serving and nutrient values; use the optional front photo only for visible product name and brand.
            Return only the JSON object required by the supplied schema. Transcribe visible values exactly. Use null for every missing, obscured, ambiguous, or uncertain field. Never guess and never convert a missing value to zero.
            referenceAmount and referenceUnit describe the labeled serving. referenceWeightGrams is the labeled gram weight for that serving. calories, proteinGrams, carbohydrateGrams, fatGrams, and fiberGrams must all be per that same labeled serving.
            """;

    private final String executable;
    private final Duration timeout;
    private final ExternalProcessRunner runner;
    private final ObjectMapper objectMapper;

    LocalCodexFoodPhotoRecognizer(
            @Value("${diet-assistant.food-import.recognizer.codex-executable:codex}") String executable,
            @Value("${diet-assistant.food-import.recognizer.timeout-seconds:300}") long timeoutSeconds,
            ExternalProcessRunner runner,
            ObjectMapper objectMapper
    ) {
        this.executable = executable;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
        this.runner = runner;
        this.objectMapper = objectMapper;
    }

    @Override
    public PendingFoodImportResult recognize(FoodPhotoRecognitionRequest request) throws FoodPhotoRecognitionException {
        Path temporaryDirectory = null;
        try {
            temporaryDirectory = Files.createTempDirectory("food-photo-recognition-");
            Path schema = temporaryDirectory.resolve("result-schema.json");
            Path output = temporaryDirectory.resolve("result.json");
            try (var input = LocalCodexFoodPhotoRecognizer.class.getResourceAsStream("/food-photo-result-schema.json")) {
                if (input == null) throw new IOException("Recognition schema is unavailable.");
                Files.copy(input, schema);
            }
            List<String> arguments = arguments(request, schema, output);
            logger.info("Food photo recognition started: importId={}, timeoutSeconds={}, executable={}",
                    request.importId(), timeout.toSeconds(), executable);
            ExternalProcessRunner.Result processResult = runner.run(arguments, timeout);
            logCompletion(request.importId(), processResult, output);
            if (processResult.exitCode() != 0) {
                throw new FoodPhotoRecognitionException(FoodPhotoRecognitionException.Reason.EXECUTION_FAILED,
                        "Local Codex recognition failed.");
            }
            if (!Files.isRegularFile(output) || Files.size(output) == 0) {
                throw new FoodPhotoRecognitionException(FoodPhotoRecognitionException.Reason.INVALID_RESULT,
                        "Local Codex returned no structured result.");
            }
            try {
                return objectMapper.readValue(output.toFile(), PendingFoodImportResult.class);
            } catch (RuntimeException exception) {
                throw new FoodPhotoRecognitionException(FoodPhotoRecognitionException.Reason.INVALID_RESULT,
                        "Local Codex returned an invalid structured result.", exception);
            }
        } catch (ExternalProcessRunner.ProcessTimeoutException exception) {
            logger.warn("Food photo recognition timed out: importId={}, elapsedMs={}, processAlive={}, stdoutBytes={}, stderrBytes={}",
                    request.importId(), exception.elapsed().toMillis(), exception.processAlive(),
                    byteLength(exception.stdout()), byteLength(exception.stderr()));
            throw new FoodPhotoRecognitionException(FoodPhotoRecognitionException.Reason.TIMEOUT,
                    "Local Codex recognition timed out.", exception);
        } catch (IOException exception) {
            logger.warn("Food photo recognition unavailable: importId={}, reason={}",
                    request.importId(), exception.getClass().getSimpleName());
            throw new FoodPhotoRecognitionException(FoodPhotoRecognitionException.Reason.UNAVAILABLE,
                    "Local Codex recognition is unavailable.", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            logger.warn("Food photo recognition interrupted: importId={}", request.importId());
            throw new FoodPhotoRecognitionException(FoodPhotoRecognitionException.Reason.EXECUTION_FAILED,
                    "Local Codex recognition was interrupted.", exception);
        } finally {
            deleteQuietly(temporaryDirectory);
        }
    }

    List<String> arguments(FoodPhotoRecognitionRequest request, Path schema, Path output) {
        ArrayList<String> arguments = new ArrayList<>(List.of(
                executable, "exec",
                "--ephemeral",
                "--ignore-user-config",
                "--ignore-rules",
                "--sandbox", "read-only",
                "--skip-git-repo-check",
                "--color", "never",
                "--cd", request.nutritionPhoto().getParent().toString(),
                "--output-schema", schema.toString(),
                "--output-last-message", output.toString(),
                "--image", request.nutritionPhoto().toString()
        ));
        request.frontPhoto().ifPresent(path -> {
            arguments.add("--image");
            arguments.add(path.toString());
        });
        arguments.add("--");
        arguments.add(PROMPT);
        return List.copyOf(arguments);
    }

    private void logCompletion(String importId, ExternalProcessRunner.Result result, Path output) {
        long resultBytes = 0;
        try { if (Files.isRegularFile(output)) resultBytes = Files.size(output); }
        catch (IOException ignored) { }
        logger.info("Food photo recognition completed: importId={}, elapsedMs={}, exitCode={}, stdoutBytes={}, stderrBytes={}, resultBytes={}",
                importId, result.elapsed().toMillis(), result.exitCode(), byteLength(result.stdout()),
                byteLength(result.stderr()), resultBytes);
    }

    private int byteLength(String value) { return value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length; }

    private void deleteQuietly(Path directory) {
        if (directory == null || !Files.exists(directory)) return;
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // The operating system can clean an abandoned recognizer temp directory.
        }
    }
}

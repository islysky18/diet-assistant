package com.chaoting.dietassistant.food;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalCodexFoodPhotoRecognizerTest {

    @TempDir Path directory;

    @Test
    void usesControlledReadOnlyCodexArgumentsAndParsesStructuredOutput() throws Exception {
        Path executable = script("""
                #!/bin/sh
                output=''
                while [ "$#" -gt 0 ]; do
                  if [ "$1" = '--output-last-message' ]; then shift; output="$1"; fi
                  shift
                done
                printf '%s' '{"name":"Oats","brand":null,"referenceAmount":1,"referenceUnit":"cup","referenceWeightGrams":40,"calories":150,"proteinGrams":5,"carbohydrateGrams":27,"fatGrams":3,"fiberGrams":4,"notes":null}' > "$output"
                """);
        var recognizer = recognizer(executable, 2);
        Path nutrition = Files.createFile(directory.resolve("nutrition.jpg"));
        Path front = Files.createFile(directory.resolve("front.jpg"));

        PendingFoodImportResult result = recognizer.recognize(request(nutrition, Optional.of(front)));
        ListAssert.assertControlled(recognizer.arguments(request(nutrition, Optional.of(front)),
                directory.resolve("schema.json"), directory.resolve("output.json")), nutrition, front);
        assertThat(result.name()).isEqualTo("Oats");
        assertThat(result.calories()).isEqualByComparingTo("150");
    }

    @Test
    void reportsNonZeroEmptyInvalidAndTimeoutResults() throws Exception {
        Path nutrition = Files.createFile(directory.resolve("nutrition.jpg"));
        assertReason(script("#!/bin/sh\nexit 7\n"), nutrition, FoodPhotoRecognitionException.Reason.EXECUTION_FAILED, 2);
        assertReason(script("#!/bin/sh\nexit 0\n"), nutrition, FoodPhotoRecognitionException.Reason.INVALID_RESULT, 2);
        assertReason(script("""
                #!/bin/sh
                while [ "$#" -gt 0 ]; do
                  if [ "$1" = '--output-last-message' ]; then shift; output="$1"; fi
                  shift
                done
                printf 'not-json' > "$output"
                """), nutrition, FoodPhotoRecognitionException.Reason.INVALID_RESULT, 2);
        assertReason(script("#!/bin/sh\nsleep 5\n"), nutrition, FoodPhotoRecognitionException.Reason.TIMEOUT, 0);
    }

    @Test
    void reportsMissingExecutableAsUnavailable() throws Exception {
        Path nutrition = Files.createFile(directory.resolve("nutrition.jpg"));

        assertReason(directory.resolve("missing-codex"), nutrition,
                FoodPhotoRecognitionException.Reason.UNAVAILABLE, 2);
    }

    private LocalCodexFoodPhotoRecognizer recognizer(Path executable, long timeoutSeconds) {
        return new LocalCodexFoodPhotoRecognizer(executable.toString(), timeoutSeconds, new ExternalProcessRunner(), new JsonMapper());
    }

    private void assertReason(Path executable, Path nutrition, FoodPhotoRecognitionException.Reason reason, long timeout) {
        assertThatThrownBy(() -> recognizer(executable, timeout).recognize(request(nutrition, Optional.empty())))
                .isInstanceOfSatisfying(FoodPhotoRecognitionException.class,
                        exception -> assertThat(exception.reason()).isEqualTo(reason));
    }

    private Path script(String content) throws Exception {
        Path script = Files.createTempFile(directory, "fake-codex-", ".sh");
        Files.writeString(script, content);
        script.toFile().setExecutable(true);
        return script;
    }

    private FoodPhotoRecognitionRequest request(Path nutrition, Optional<Path> front) {
        return new FoodPhotoRecognitionRequest("11111111-1111-4111-8111-111111111111", nutrition, front);
    }

    private static final class ListAssert {
        static void assertControlled(java.util.List<String> arguments, Path nutrition, Path front) {
            assertThat(arguments).containsSubsequence("exec", "--ephemeral", "--ignore-user-config", "--ignore-rules",
                    "--sandbox", "read-only", "--skip-git-repo-check", "--color", "never");
            assertThat(arguments).contains("--output-schema", "--output-last-message", nutrition.toAbsolutePath().toString(), front.toAbsolutePath().toString());
            assertThat(arguments.get(arguments.size() - 2)).isEqualTo("--");
            assertThat(arguments).doesNotContain("--dangerously-bypass-approvals-and-sandbox", "workspace-write", "danger-full-access");
            assertThat(arguments).noneMatch(argument -> argument.contains("original"));
        }
    }
}

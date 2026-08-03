package com.chaoting.dietassistant.food;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class FoodImportProcessingUiTest {

    @Test
    void processingUiHasAccessibleStatusSpinnerPollingConfigurationAndManualFallback() throws IOException {
        String template = template();

        assertThat(template).contains(
                "role=\"status\"",
                "aria-live=\"polite\"",
                "class=\"spinner\" aria-hidden=\"true\"",
                "Photos are being analyzed. This usually takes a few seconds.",
                "data-poll-interval-ms=2500",
                "data-poll-limit-ms=300000",
                "Refresh Status"
        );
    }

    @Test
    void pollingIsProcessingOnlySequentialAndStopsForTerminalExpiryAndTimeLimit() throws IOException {
        String template = template();

        assertThat(template).contains(
                "th:if=\"${pendingImport.status == 'processing'}\"",
                "window.setTimeout(poll, intervalMs)",
                "const response = await fetch",
                "if (payload.status === 'processing')",
                "response.status === 404 || response.status === 410",
                "Date.now() - startedAt >= limitMs",
                "window.location.assign(statusRegion.dataset.reviewUrl)",
                "catch (error)",
                "schedule();",
                "window.addEventListener('pagehide', () => stop())"
        );
        assertThat(template).doesNotContain("setInterval(");
    }

    @Test
    void reviewAndFailurePagesDoNotRenderProcessingRegionAndFormsGuardDuplicateSubmit() throws IOException {
        String template = template();

        assertThat(template).contains(
                "pendingImport.status == 'ready_for_review' or pendingImport.status == 'recognition_failed'",
                "class=\"form-grid js-loading-form\"",
                "form.dataset.submitting === 'true'",
                "event.preventDefault()",
                "button.disabled = true",
                "data-loading-label=\"Processing...\""
        );
    }

    private String template() throws IOException {
        try (var input = getClass().getResourceAsStream("/templates/food-import.html")) {
            if (input == null) throw new IOException("food-import.html not found");
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}

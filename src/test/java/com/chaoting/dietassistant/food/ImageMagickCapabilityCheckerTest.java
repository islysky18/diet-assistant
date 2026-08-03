package com.chaoting.dietassistant.food;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ImageMagickCapabilityCheckerTest {

    @Test
    void missingExecutableReturnsUnavailableWithoutThrowing() {
        var checker = new ImageMagickCapabilityChecker("/definitely/missing/magick", 1, new ExternalProcessRunner());
        var capabilities = checker.capabilities();
        assertThat(capabilities.normalizationAvailable()).isFalse();
        assertThat(capabilities.heicReadAvailable()).isFalse();
        assertThat(capabilities.message()).contains("manual Saved Food remains available");
    }
}

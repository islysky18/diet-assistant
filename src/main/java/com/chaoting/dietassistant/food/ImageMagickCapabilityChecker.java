package com.chaoting.dietassistant.food;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.List;

@Component
class ImageMagickCapabilityChecker {

    private static final String HEIC_PROBE = "AAAAHGZ0eXBoZWljAAAAAG1pZjFoZWljbWlhZgAAAXxtZXRhAAAAAAAAACFoZGxyAAAAAAAAAABwaWN0AAAAAAAAAAAAAAAAAAAAACJpbG9jAAAAAERAAAEAAQAAAAABoAABAAAAAAAAADYAAAAjaWluZgAAAAAAAQAAABVpbmZlAgAAAAABAABodmMxAAAAAA5waXRtAAAAAAABAAAA/GlwcnAAAADcaXBjbwAAAHVodmNDAQNwAAAAAAAAAAAAHvAA/P34+AAADwNgAAEAGEABDAH//wNwAAADAJAAAAMAAAMAHroCQGEAAQApQgEBA3AAAAMAkAAAAwAAAwAeoCCBBZbqrprm4CGgwIAAAAyAAAADAIRiAAEABkQBwXPBiQAAABNjb2xybmNseAABAA0ABoAAAAAUaXNwZQAAAAAAAABAAAAAQAAAAChjbGFwAAAAAgAAAAEAAAACAAAAAf///8IAAAAC////wgAAAAIAAAAQcGl4aQAAAAADCAgIAAAAGGlwbWEAAAAAAAAAAQABBYECAwWEAAAAPm1kYXQAAAAyKAGvBjIWZzSJIPC9fH/X/kV///y0Ss1snrhH6GPH5LeQkZ70X3sYUuU8dCD0nFO5zYA=";

    private final String executable;
    private final Duration timeout;
    private final ExternalProcessRunner runner;
    private volatile Capabilities cached;

    ImageMagickCapabilityChecker(
            @Value("${diet-assistant.food-import.imagemagick.executable:magick}") String executable,
            @Value("${diet-assistant.food-import.imagemagick.timeout-seconds:30}") long timeoutSeconds,
            ExternalProcessRunner runner
    ) {
        this.executable = executable;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
        this.runner = runner;
    }

    Capabilities capabilities() {
        Capabilities result = cached;
        if (result == null) {
            synchronized (this) {
                if (cached == null) cached = check();
                result = cached;
            }
        }
        return result;
    }

    void clearCache() { cached = null; }

    private Capabilities check() {
        try {
            ExternalProcessRunner.Result version = runner.run(List.of(executable, "-version"), timeout);
            if (version.exitCode() != 0 || !version.output().contains("ImageMagick 7.")) {
                return new Capabilities(false, false, "ImageMagick 7 is unavailable. Configure diet-assistant.food-import.imagemagick.executable.");
            }
            Path directory = Files.createTempDirectory("food-import-capability-");
            try {
                Path input = directory.resolve("probe.heic");
                Path output = directory.resolve("probe.jpg");
                Files.write(input, Base64.getDecoder().decode(HEIC_PROBE));
                ExternalProcessRunner.Result decode = runner.run(List.of(executable, input + "[0]", "-auto-orient", "-strip", output.toString()), timeout);
                boolean heic = decode.exitCode() == 0 && Files.size(output) > 3 && isJpeg(output);
                return new Capabilities(true, heic, heic ? null : "ImageMagick 7 is installed but cannot decode HEIC/HEIF. Install libheif support.");
            } finally {
                try (var paths = Files.walk(directory)) {
                    for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
                }
            }
        } catch (Exception exception) {
            return new Capabilities(false, false, "ImageMagick 7 is unavailable. Photo import is disabled; manual Saved Food remains available.");
        }
    }

    private boolean isJpeg(Path path) throws java.io.IOException {
        byte[] bytes = Files.readAllBytes(path);
        return bytes.length >= 3 && bytes[0] == (byte) 0xff && bytes[1] == (byte) 0xd8 && bytes[2] == (byte) 0xff;
    }

    record Capabilities(boolean normalizationAvailable, boolean heicReadAvailable, String message) { }
}

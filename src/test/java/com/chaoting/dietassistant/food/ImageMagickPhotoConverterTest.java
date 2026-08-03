package com.chaoting.dietassistant.food;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImageMagickPhotoConverterTest {

    @TempDir Path directory;

    @Test
    void rejectsEmptyAndCorruptConverterOutput() throws Exception {
        Path fake = fakeExecutable("""
                #!/bin/sh
                for arg in "$@"; do [ "$arg" = "info:" ] && { printf '2 2'; exit 0; }; done
                for last do :; done
                : > "$last"
                """);
        Path input = directory.resolve("input.png");
        Files.write(input, new byte[]{1});
        var converter = new ImageMagickPhotoConverter(fake.toString(), 2, 100, 100, 10000, 95, new ExternalProcessRunner());
        assertThatThrownBy(() -> converter.normalize(input, directory.resolve("output.jpg")))
                .isInstanceOf(java.io.IOException.class).hasMessageContaining("invalid or empty JPEG");
    }

    @Test
    void rejectsNonZeroExitAndExcessivePixels() throws Exception {
        Path failing = fakeExecutable("#!/bin/sh\necho conversion-failed >&2\nexit 9\n");
        var failingConverter = new ImageMagickPhotoConverter(failing.toString(), 2, 100, 100, 10000, 95, new ExternalProcessRunner());
        assertThatThrownBy(() -> failingConverter.normalize(directory.resolve("x"), directory.resolve("y")))
                .isInstanceOf(java.io.IOException.class).hasMessageContaining("could not read");

        Path huge = fakeExecutable("#!/bin/sh\nprintf '100 100'\n");
        var limited = new ImageMagickPhotoConverter(huge.toString(), 2, 100, 100, 9999, 95, new ExternalProcessRunner());
        assertThatThrownBy(() -> limited.normalize(directory.resolve("x"), directory.resolve("y")))
                .isInstanceOf(java.io.IOException.class).hasMessageContaining("pixel limit");
    }

    private Path fakeExecutable(String content) throws Exception {
        Path path = Files.createTempFile(directory, "fake-magick-", ".sh");
        Files.writeString(path, content);
        path.toFile().setExecutable(true);
        return path;
    }
}

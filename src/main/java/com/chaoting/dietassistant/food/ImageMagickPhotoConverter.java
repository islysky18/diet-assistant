package com.chaoting.dietassistant.food;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Component
class ImageMagickPhotoConverter {

    private final String executable;
    private final Duration timeout;
    private final long maxWidth;
    private final long maxHeight;
    private final long maxPixels;
    private final int quality;
    private final ExternalProcessRunner runner;

    ImageMagickPhotoConverter(
            @Value("${diet-assistant.food-import.imagemagick.executable:magick}") String executable,
            @Value("${diet-assistant.food-import.imagemagick.timeout-seconds:30}") long timeoutSeconds,
            @Value("${diet-assistant.food-import.image.max-width:12000}") long maxWidth,
            @Value("${diet-assistant.food-import.image.max-height:12000}") long maxHeight,
            @Value("${diet-assistant.food-import.image.max-pixels:40000000}") long maxPixels,
            @Value("${diet-assistant.food-import.image.jpeg-quality:95}") int quality,
            ExternalProcessRunner runner
    ) {
        this.executable = executable;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
        this.maxWidth = maxWidth;
        this.maxHeight = maxHeight;
        this.maxPixels = maxPixels;
        this.quality = quality;
        this.runner = runner;
    }

    void normalize(Path input, Path output) throws IOException {
        Dimensions source = identify(input);
        validateDimensions(source);
        List<String> arguments = baseArguments();
        arguments.add(input + "[0]");
        arguments.add("-auto-orient");
        arguments.add("-resize");
        arguments.add(maxWidth + "x" + maxHeight + ">");
        arguments.add("-strip");
        arguments.add("-colorspace");
        arguments.add("sRGB");
        arguments.add("-quality");
        arguments.add(Integer.toString(quality));
        arguments.add(output.toString());
        run(arguments, "ImageMagick could not normalize the photo.");
        if (!Files.isRegularFile(output) || Files.size(output) < 4 || !isJpeg(output)) {
            throw new IOException("ImageMagick produced an invalid or empty JPEG.");
        }
        validateDimensions(readJpegDimensions(output));
    }

    private Dimensions identify(Path input) throws IOException {
        List<String> arguments = baseArguments();
        arguments.add(input + "[0]");
        arguments.add("-ping");
        arguments.add("-format");
        arguments.add("%w %h");
        arguments.add("info:");
        ExternalProcessRunner.Result result = run(arguments, "ImageMagick could not read the photo.");
        String[] parts = result.output().trim().split("\\s+");
        if (parts.length != 2) throw new IOException("Image dimensions could not be determined.");
        try { return new Dimensions(Long.parseLong(parts[0]), Long.parseLong(parts[1])); }
        catch (NumberFormatException exception) { throw new IOException("Image dimensions are invalid.", exception); }
    }

    private List<String> baseArguments() {
        ArrayList<String> args = new ArrayList<>(List.of(executable,
                "-limit", "memory", "256MiB", "-limit", "map", "512MiB", "-limit", "disk", "1GiB",
                "-limit", "thread", "2", "-limit", "width", maxWidth + "P", "-limit", "height", maxHeight + "P",
                "-limit", "time", Long.toString(timeout.toSeconds())));
        return args;
    }

    private ExternalProcessRunner.Result run(List<String> args, String message) throws IOException {
        try {
            ExternalProcessRunner.Result result = runner.run(List.copyOf(args), timeout);
            if (result.exitCode() != 0) throw new IOException(message);
            return result;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Image processing was interrupted.", exception);
        }
    }

    private void validateDimensions(Dimensions dimensions) throws IOException {
        if (dimensions.width <= 0 || dimensions.height <= 0 || dimensions.width > maxWidth || dimensions.height > maxHeight) {
            throw new IOException("Photo dimensions exceed the configured width or height limit.");
        }
        long pixels;
        try { pixels = Math.multiplyExact(dimensions.width, dimensions.height); }
        catch (ArithmeticException exception) { throw new IOException("Photo pixel dimensions overflow.", exception); }
        if (pixels > maxPixels) throw new IOException("Photo exceeds the configured total pixel limit.");
    }

    private Dimensions readJpegDimensions(Path path) throws IOException {
        var image = ImageIO.read(path.toFile());
        if (image == null) throw new IOException("Prepared JPEG cannot be decoded.");
        return new Dimensions(image.getWidth(), image.getHeight());
    }

    private boolean isJpeg(Path path) throws IOException {
        try (var input = Files.newInputStream(path)) {
            byte[] signature = input.readNBytes(3);
            return signature.length == 3 && signature[0] == (byte) 0xff && signature[1] == (byte) 0xd8 && signature[2] == (byte) 0xff;
        }
    }

    private record Dimensions(long width, long height) { }
}

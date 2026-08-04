package com.chaoting.dietassistant.food;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ProductPhotoHeicIntegrationTest {

    @TempDir Path directory;
    private byte[] jpeg;
    private byte[] heic;
    private PendingFoodImportService service;

    @BeforeEach
    void setUp() throws Exception {
        assumeTrue(command("magick", "-version") == 0, "ImageMagick 7 is required for HEIC integration tests");
        Path png = directory.resolve("synthetic.png");
        Path jpegPath = directory.resolve("synthetic.jpg");
        Path heicPath = directory.resolve("synthetic.heic");
        assertThat(command("magick", "-size", "4x3", "xc:#4b7bec", png.toString())).isZero();
        assertThat(command("magick", png.toString(), jpegPath.toString())).isZero();
        assertThat(command("heif-enc", "--quality", "90", "--output", heicPath.toString(), png.toString())).isZero();
        jpeg = Files.readAllBytes(jpegPath);
        heic = Files.readAllBytes(heicPath);
        ExternalProcessRunner runner = new ExternalProcessRunner();
        var checker = new ImageMagickCapabilityChecker("magick", 30, runner);
        assumeTrue(checker.capabilities().heicReadAvailable(), "ImageMagick must actually decode HEIC");
        service = new PendingFoodImportService(directory.resolve("pending"), 10_485_760, 24, new JsonMapper(), Clock.systemUTC(),
                new ProductPhotoValidator(), new ImageMagickPhotoConverter("magick", 30, 12000, 12000, 40_000_000, 95, runner), checker);
    }

    @Test
    void importsHeicFrontWithJpegNutritionAndCleansEverything() {
        PendingFoodImportView pending = service.create(request(photo("front.heic", "image/heic", heic), photo("facts.jpg", "image/jpeg", jpeg)));
        Path importDirectory = service.importDirectory(pending.importId());
        assertThat(importDirectory.resolve("front-original.heic")).exists();
        assertThat(importDirectory.resolve("front.jpg")).exists();
        assertThat(importDirectory.resolve("nutrition-original.jpg")).exists();
        assertThat(importDirectory.resolve("nutrition.jpg")).exists();
        assertThat(service.delete(pending.importId())).isTrue();
        assertThat(importDirectory).doesNotExist();
    }

    @Test
    void importsJpegFrontWithHeicNutritionAndValidHeifNutrition() {
        PendingFoodImportView mixed = service.create(request(photo("front.jpeg", "image/jpeg", jpeg), photo("facts.heic", "image/heic", heic)));
        assertThat(service.importDirectory(mixed.importId()).resolve("nutrition.jpg")).exists();
        service.delete(mixed.importId());

        PendingFoodImportView heifImport = service.create(request(null, photo("facts.heif", "image/heif", heic)));
        assertThat(service.importDirectory(heifImport.importId()).resolve("nutrition-original.heif")).exists();
    }

    private int command(String... arguments) throws Exception {
        return new ProcessBuilder(arguments).redirectErrorStream(true).start().waitFor();
    }
    private MockMultipartFile photo(String name, String mime, byte[] bytes) { return new MockMultipartFile("photo", name, mime, bytes); }
    private SavedFoodPhotoUploadRequest request(MockMultipartFile front, MockMultipartFile nutrition) {
        SavedFoodPhotoUploadRequest request = new SavedFoodPhotoUploadRequest(); request.setFrontPhoto(front); request.setNutritionFactsPhoto(nutrition); return request;
    }
}

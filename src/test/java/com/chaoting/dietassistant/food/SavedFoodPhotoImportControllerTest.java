package com.chaoting.dietassistant.food;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ConcurrentModel;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class SavedFoodPhotoImportControllerTest {

    private FakePendingFoodImportService pendingFoodImportService;
    private FakeSavedFoodService savedFoodService;
    private SavedFoodPhotoImportController controller;

    @BeforeEach
    void setUp() {
        pendingFoodImportService = new FakePendingFoodImportService();
        savedFoodService = new FakeSavedFoodService();
        controller = new SavedFoodPhotoImportController(pendingFoodImportService, savedFoodService);
    }

    @Test
    void uploadRequiresCurrentProfileAndDoesNotCreatePendingFiles() {
        savedFoodService.hasProfile = false;
        SavedFoodPhotoUploadRequest request = new SavedFoodPhotoUploadRequest();

        String view = controller.upload(
                request,
                new BeanPropertyBindingResult(request, "photoUploadRequest"),
                new RedirectAttributesModelMap()
        );

        assertThat(view).isEqualTo("redirect:/profile");
        assertThat(pendingFoodImportService.createCalled).isFalse();
    }

    @Test
    void invalidConfirmationDoesNotWriteSavedFoodOrDeletePendingImport() {
        PendingFoodImportView pending = pending("123e4567-e89b-12d3-a456-426614174000");
        pendingFoodImportService.pending = Optional.of(pending);
        SavedFoodRequest request = new SavedFoodRequest();
        BeanPropertyBindingResult errors = new BeanPropertyBindingResult(request, "savedFoodRequest");
        errors.rejectValue("calories", "missing", "Calories are required.");

        String view = controller.confirm(
                pending.importId(),
                request,
                errors,
                new ConcurrentModel(),
                new RedirectAttributesModelMap()
        );

        assertThat(view).isEqualTo("food-import");
        assertThat(savedFoodService.createdRequest).isNull();
        assertThat(pendingFoodImportService.confirmCalled).isFalse();
    }

    @Test
    void validConfirmationUsesExistingProfileScopedCreateThenDeletesPendingImport() {
        PendingFoodImportView pending = pending("123e4567-e89b-12d3-a456-426614174000");
        pendingFoodImportService.pending = Optional.of(pending);
        SavedFoodRequest request = new SavedFoodRequest();
        BeanPropertyBindingResult errors = new BeanPropertyBindingResult(request, "savedFoodRequest");

        String view = controller.confirm(
                pending.importId(),
                request,
                errors,
                new ConcurrentModel(),
                new RedirectAttributesModelMap()
        );

        assertThat(view).isEqualTo("redirect:/foods");
        assertThat(savedFoodService.createdRequest).isSameAs(request);
        assertThat(pendingFoodImportService.confirmedImportId).isEqualTo(pending.importId());
    }

    @Test
    void statusEndpointReturnsOnlyCurrentStatusWithoutStartingRecognition() {
        pendingFoodImportService.status = Optional.of("processing");

        var response = controller.status("123e4567-e89b-12d3-a456-426614174000");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(new SavedFoodPhotoImportController.ImportStatusResponse("processing"));
        assertThat(pendingFoodImportService.retryCalled).isFalse();
        assertThat(pendingFoodImportService.createCalled).isFalse();
        assertThat(SavedFoodPhotoImportController.ImportStatusResponse.class.getRecordComponents())
                .extracting(component -> component.getName())
                .containsExactly("status");
    }

    @Test
    void statusEndpointReturnsEveryRecognitionTerminalStatus() {
        for (String status : new String[]{"ready_for_review", "recognition_failed", "recognition_timed_out"}) {
            pendingFoodImportService.status = Optional.of(status);
            var response = controller.status("123e4567-e89b-12d3-a456-426614174000");
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().status()).isEqualTo(status);
        }
    }

    @Test
    void statusEndpointSafelyHandlesExpiredMissingAndInvalidImports() {
        pendingFoodImportService.status = Optional.of("expired");
        assertThat(controller.status("123e4567-e89b-12d3-a456-426614174000").getStatusCode())
                .isEqualTo(HttpStatus.GONE);

        pendingFoodImportService.status = Optional.empty();
        assertThat(controller.status("123e4567-e89b-12d3-a456-426614174000").getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);

        pendingFoodImportService.throwInvalidStatusId = true;
        assertThat(controller.status("invalid").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private PendingFoodImportView pending(String importId) {
        return new PendingFoodImportView(
                importId,
                "pending",
                LocalDateTime.of(2026, 8, 2, 9, 0),
                LocalDateTime.of(2026, 8, 3, 9, 0),
                false,
                "nutrition-facts.jpg",
                null,
                false,
                null
        );
    }

    private static final class FakePendingFoodImportService extends PendingFoodImportService {
        private Optional<PendingFoodImportView> pending = Optional.empty();
        private boolean createCalled;
        private String deletedImportId;
        private String confirmedImportId;
        private boolean confirmCalled;
        private Optional<String> status = Optional.empty();
        private boolean retryCalled;
        private boolean throwInvalidStatusId;

        private FakePendingFoodImportService() {
            super(Path.of("unused"), 10, 24, new JsonMapper(), Clock.systemUTC());
        }

        @Override
        public boolean validateUpload(SavedFoodPhotoUploadRequest request, org.springframework.validation.BindingResult bindingResult) {
            return true;
        }

        @Override
        public PendingFoodImportView create(SavedFoodPhotoUploadRequest request) {
            createCalled = true;
            return pending.orElseThrow();
        }

        @Override
        public Optional<PendingFoodImportView> find(String importId) {
            return pending;
        }

        @Override
        public boolean delete(String importId) {
            deletedImportId = importId;
            return true;
        }

        @Override
        public boolean confirm(String importId, Runnable createSavedFood) {
            confirmCalled = true;
            confirmedImportId = importId;
            createSavedFood.run();
            return true;
        }

        @Override
        public Optional<String> currentStatus(String importId) {
            if (throwInvalidStatusId) throw new IllegalArgumentException("invalid");
            return status;
        }

        @Override
        public boolean retryRecognition(String importId) {
            retryCalled = true;
            return true;
        }
    }

    private static final class FakeSavedFoodService extends SavedFoodService {
        private boolean hasProfile = true;
        private SavedFoodRequest createdRequest;

        private FakeSavedFoodService() {
            super(null, Optional::empty, Clock.systemUTC());
        }

        @Override
        public boolean hasCurrentProfile() {
            return hasProfile;
        }

        @Override
        public SavedFoodResponse create(SavedFoodRequest request) {
            createdRequest = request;
            return null;
        }
    }
}

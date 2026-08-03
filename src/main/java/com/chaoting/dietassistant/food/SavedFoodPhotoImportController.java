package com.chaoting.dietassistant.food;

import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class SavedFoodPhotoImportController {

    private final PendingFoodImportService pendingFoodImportService;
    private final SavedFoodService savedFoodService;

    public SavedFoodPhotoImportController(
            PendingFoodImportService pendingFoodImportService,
            SavedFoodService savedFoodService
    ) {
        this.pendingFoodImportService = pendingFoodImportService;
        this.savedFoodService = savedFoodService;
    }

    @ModelAttribute("imageRuntime")
    public ImageMagickCapabilityChecker.Capabilities imageRuntime() {
        return pendingFoodImportService.capabilities();
    }

    @GetMapping("/foods/import")
    public String showUpload(Model model, RedirectAttributes redirectAttributes) {
        if (!savedFoodService.hasCurrentProfile()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Create your profile before importing a food.");
            return "redirect:/profile";
        }
        if (!model.containsAttribute("photoUploadRequest")) {
            model.addAttribute("photoUploadRequest", new SavedFoodPhotoUploadRequest());
        }
        return "food-import";
    }

    @PostMapping("/foods/import")
    public String upload(
            @ModelAttribute("photoUploadRequest") SavedFoodPhotoUploadRequest photoUploadRequest,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes
    ) {
        if (!savedFoodService.hasCurrentProfile()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Create your profile before importing a food.");
            return "redirect:/profile";
        }
        if (!pendingFoodImportService.validateUpload(photoUploadRequest, bindingResult)) {
            return "food-import";
        }
        PendingFoodImportView pendingImport;
        try {
            pendingImport = pendingFoodImportService.create(photoUploadRequest);
        } catch (IllegalStateException exception) {
            bindingResult.reject("photo.processing", exception.getMessage());
            return "food-import";
        }
        redirectAttributes.addFlashAttribute("successMessage", "Photos uploaded. The pending import is ready for Codex preparation.");
        return "redirect:/foods/import/" + pendingImport.importId();
    }

    @GetMapping("/foods/import/{importId}")
    public String review(
            @PathVariable String importId,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        if (!savedFoodService.hasCurrentProfile()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Create your profile before importing a food.");
            return "redirect:/profile";
        }
        try {
            return pendingFoodImportService.find(importId)
                    .map(pendingImport -> {
                        model.addAttribute("pendingImport", pendingImport);
                        if (!model.containsAttribute("savedFoodRequest")) {
                            model.addAttribute("savedFoodRequest", pendingFoodImportService.loadResult(importId)
                                    .orElseGet(SavedFoodRequest::new));
                        }
                        return "food-import";
                    })
                    .orElseGet(() -> redirectMissing(redirectAttributes));
        } catch (IllegalArgumentException exception) {
            return redirectMissing(redirectAttributes);
        }
    }

    @GetMapping("/foods/import/{importId}/status")
    @ResponseBody
    public ResponseEntity<ImportStatusResponse> status(@PathVariable String importId) {
        try {
            return pendingFoodImportService.currentStatus(importId)
                    .map(status -> "expired".equals(status)
                            ? ResponseEntity.status(410).body(new ImportStatusResponse(status))
                            : ResponseEntity.ok(new ImportStatusResponse(status)))
                    .orElseGet(() -> ResponseEntity.notFound().build());
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/foods/import/{importId}/confirm")
    public String confirm(
            @PathVariable String importId,
            @Valid @ModelAttribute("savedFoodRequest") SavedFoodRequest savedFoodRequest,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        if (!savedFoodService.hasCurrentProfile()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Create your profile before importing a food.");
            return "redirect:/profile";
        }
        PendingFoodImportView pendingImport;
        try {
            pendingImport = pendingFoodImportService.find(importId).orElse(null);
        } catch (IllegalArgumentException exception) {
            pendingImport = null;
        }
        if (pendingImport == null) {
            return redirectMissing(redirectAttributes);
        }
        if (bindingResult.hasErrors()) {
            model.addAttribute("pendingImport", pendingImport);
            return "food-import";
        }
        if (!pendingFoodImportService.confirm(importId, () -> savedFoodService.create(savedFoodRequest))) {
            return redirectMissing(redirectAttributes);
        }
        redirectAttributes.addFlashAttribute("successMessage", "Saved food created and pending photos deleted.");
        return "redirect:/foods";
    }

    @PostMapping("/foods/import/{importId}/cancel")
    public String cancel(@PathVariable String importId, RedirectAttributes redirectAttributes) {
        try {
            pendingFoodImportService.delete(importId);
        } catch (IllegalArgumentException exception) {
            // Invalid and already-missing ids have the same user-facing outcome.
        }
        redirectAttributes.addFlashAttribute("successMessage", "Pending food import cancelled and temporary files deleted.");
        return "redirect:/foods";
    }

    @PostMapping("/foods/import/{importId}/retry")
    public String retry(@PathVariable String importId, RedirectAttributes redirectAttributes) {
        try {
            if (pendingFoodImportService.retryRecognition(importId)) {
                redirectAttributes.addFlashAttribute("successMessage", "Automatic recognition restarted.");
                return "redirect:/foods/import/" + importId;
            }
        } catch (IllegalArgumentException exception) {
            // Invalid and missing ids share the normal missing-import response.
        }
        return redirectMissing(redirectAttributes);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String uploadTooLarge(Model model) {
        SavedFoodPhotoUploadRequest request = new SavedFoodPhotoUploadRequest();
        model.addAttribute("photoUploadRequest", request);
        model.addAttribute("uploadError", "Upload is too large. Each photo must be at most 10 MB and the combined request at most 20 MB.");
        return "food-import";
    }

    private String redirectMissing(RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("successMessage", "Pending food import not found or expired.");
        return "redirect:/foods";
    }

    public record ImportStatusResponse(String status) { }
}

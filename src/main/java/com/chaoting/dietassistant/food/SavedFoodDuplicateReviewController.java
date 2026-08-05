package com.chaoting.dietassistant.food;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class SavedFoodDuplicateReviewController {

    private final SavedFoodDuplicateReviewService duplicateReviewService;

    public SavedFoodDuplicateReviewController(SavedFoodDuplicateReviewService duplicateReviewService) {
        this.duplicateReviewService = duplicateReviewService;
    }

    @GetMapping("/foods/duplicates/{token}")
    public String review(@PathVariable String token, Model model, RedirectAttributes redirectAttributes) {
        return duplicateReviewService.find(token)
                .map(review -> {
                    if (review.candidates().isEmpty()) {
                        preserveAndReturn(review, redirectAttributes,
                                "The possible duplicate is no longer active. Review the food and submit again.");
                        return sourceRedirect(review);
                    }
                    model.addAttribute("duplicateReview", review);
                    return "food-duplicate-review";
                })
                .orElseGet(() -> missing(redirectAttributes));
    }

    @PostMapping("/foods/duplicates/{token}/use-existing")
    public String useExisting(@PathVariable String token, @RequestParam Long candidateId,
                              RedirectAttributes redirectAttributes) {
        var review = duplicateReviewService.find(token).orElse(null);
        if (review == null) return missing(redirectAttributes);
        try {
            var outcome = duplicateReviewService.useExisting(token, candidateId);
            if (outcome == SavedFoodDuplicateReviewService.Outcome.USED_EXISTING) {
                redirectAttributes.addFlashAttribute("successMessage", review.source() == SavedFoodDuplicateReviewService.Source.PHOTO
                        ? "Existing saved food used and pending photos deleted." : "Existing saved food kept; no duplicate was created.");
                return "redirect:/foods";
            }
            if (outcome == SavedFoodDuplicateReviewService.Outcome.CANDIDATE_UNAVAILABLE) {
                preserveAndReturn(review, redirectAttributes,
                        "That saved food is no longer an active duplicate. Review the food and submit again.");
                return sourceRedirect(review);
            }
            return missing(redirectAttributes);
        } catch (SavedFoodDuplicateReviewService.CandidateUnavailableException exception) {
            preserveAndReturn(review, redirectAttributes,
                    "That saved food became inactive. Review the food and submit again.");
            return sourceRedirect(review);
        }
    }

    @PostMapping("/foods/duplicates/{token}/create-anyway")
    public String createAnyway(@PathVariable String token, RedirectAttributes redirectAttributes) {
        var outcome = duplicateReviewService.createAnyway(token);
        if (outcome != SavedFoodDuplicateReviewService.Outcome.CREATED) return missing(redirectAttributes);
        redirectAttributes.addFlashAttribute("successMessage", "Saved food created after duplicate review.");
        return "redirect:/foods";
    }

    @PostMapping("/foods/duplicates/{token}/back")
    public String back(@PathVariable String token, RedirectAttributes redirectAttributes) {
        return duplicateReviewService.find(token)
                .map(review -> {
                    preserveAndReturn(review, redirectAttributes, null);
                    return sourceRedirect(review);
                })
                .orElseGet(() -> missing(redirectAttributes));
    }

    private void preserveAndReturn(SavedFoodDuplicateReviewService.ReviewView review,
                                   RedirectAttributes redirectAttributes, String error) {
        redirectAttributes.addFlashAttribute("savedFoodRequest", review.requested());
        if (error != null) redirectAttributes.addFlashAttribute("errorMessage", error);
    }

    private String sourceRedirect(SavedFoodDuplicateReviewService.ReviewView review) {
        return review.source() == SavedFoodDuplicateReviewService.Source.PHOTO
                ? "redirect:/foods/import/" + review.importId() : "redirect:/foods";
    }

    private String missing(RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("errorMessage", "Duplicate review expired or was already completed.");
        return "redirect:/foods";
    }
}

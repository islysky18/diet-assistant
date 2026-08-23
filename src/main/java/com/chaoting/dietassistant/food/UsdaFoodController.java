package com.chaoting.dietassistant.food;

import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class UsdaFoodController {

    private final UsdaFoodImportService usdaService;
    private final SavedFoodService savedFoodService;
    private final SavedFoodDuplicateReviewService duplicateReviewService;

    public UsdaFoodController(UsdaFoodImportService usdaService, SavedFoodService savedFoodService,
                              SavedFoodDuplicateReviewService duplicateReviewService) {
        this.usdaService = usdaService;
        this.savedFoodService = savedFoodService;
        this.duplicateReviewService = duplicateReviewService;
    }

    @GetMapping("/foods/usda")
    public String search(@RequestParam(defaultValue = "") String q, Model model,
                         RedirectAttributes redirectAttributes) {
        if (!savedFoodService.hasCurrentProfile()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Create your profile before importing a USDA food.");
            return "redirect:/profile";
        }
        model.addAttribute("searchQuery", q);
        model.addAttribute("usdaConfigured", usdaService.isConfigured());
        if (!q.isBlank() && usdaService.isConfigured()) {
            try {
                model.addAttribute("searchResults", usdaService.search(q));
                model.addAttribute("searchPerformed", true);
            } catch (UsdaFoodDataException exception) {
                model.addAttribute("usdaError", exception.getMessage());
            }
        }
        return "food-usda-search";
    }

    @GetMapping("/foods/usda/{fdcId}")
    public String preview(@PathVariable long fdcId, Model model, RedirectAttributes redirectAttributes) {
        if (!savedFoodService.hasCurrentProfile()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Create your profile before importing a USDA food.");
            return "redirect:/profile";
        }
        if (fdcId <= 0) return unavailable(redirectAttributes, "Invalid USDA food identifier.");
        try {
            if (!model.containsAttribute("savedFoodRequest")) {
                model.addAttribute("savedFoodRequest", usdaService.prepareImport(fdcId));
            }
            model.addAttribute("fdcId", fdcId);
            return "food-usda-import";
        } catch (UsdaFoodDataException exception) {
            return unavailable(redirectAttributes, exception.getMessage());
        }
    }

    @PostMapping("/foods/usda/{fdcId}")
    public String confirm(@PathVariable long fdcId,
                          @Valid @ModelAttribute("savedFoodRequest") SavedFoodRequest request,
                          BindingResult bindingResult, Model model, RedirectAttributes redirectAttributes) {
        if (!savedFoodService.hasCurrentProfile()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Create your profile before importing a USDA food.");
            return "redirect:/profile";
        }
        if (fdcId <= 0) return unavailable(redirectAttributes, "Invalid USDA food identifier.");
        if (bindingResult.hasErrors()) {
            model.addAttribute("fdcId", fdcId);
            return "food-usda-import";
        }
        var duplicateToken = duplicateReviewService.beginUsda(Long.toString(fdcId), request);
        if (duplicateToken.isPresent()) return "redirect:/foods/duplicates/" + duplicateToken.orElseThrow();
        savedFoodService.create(request);
        redirectAttributes.addFlashAttribute("successMessage", "USDA food imported.");
        return "redirect:/foods";
    }

    private String unavailable(RedirectAttributes attributes, String message) {
        attributes.addFlashAttribute("errorMessage", message);
        return "redirect:/foods/usda";
    }
}

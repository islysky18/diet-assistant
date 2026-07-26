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
public class SavedFoodController {

    private final SavedFoodService savedFoodService;

    public SavedFoodController(SavedFoodService savedFoodService) {
        this.savedFoodService = savedFoodService;
    }

    @GetMapping("/foods")
    public String showFoods(
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "false") boolean includeInactive,
            Model model
    ) {
        if (!model.containsAttribute("savedFoodRequest")) {
            model.addAttribute("savedFoodRequest", new SavedFoodRequest());
        }
        addFoodsModelAttributes(model, q, includeInactive);
        return "foods";
    }

    @PostMapping("/foods")
    public String createSavedFood(
            @Valid @ModelAttribute("savedFoodRequest") SavedFoodRequest savedFoodRequest,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            addFoodsModelAttributes(model);
            return "foods";
        }

        savedFoodService.create(savedFoodRequest);
        redirectAttributes.addFlashAttribute("successMessage", "Saved food created.");
        return "redirect:/foods";
    }

    @GetMapping("/foods/{id}/edit")
    public String editSavedFood(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        return savedFoodService.getSavedFood(id)
                .map(savedFood -> {
                    if (!model.containsAttribute("savedFoodRequest")) {
                        model.addAttribute("savedFoodRequest", savedFoodService.toRequest(savedFood));
                    }
                    model.addAttribute("savedFood", savedFood);
                    return "food-edit";
                })
                .orElseGet(() -> {
                    redirectAttributes.addFlashAttribute("successMessage", "Saved food not found.");
                    return "redirect:/foods";
                });
    }

    @PostMapping("/foods/{id}")
    public String updateSavedFood(
            @PathVariable Long id,
            @Valid @ModelAttribute("savedFoodRequest") SavedFoodRequest savedFoodRequest,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        return savedFoodService.getSavedFood(id)
                .map(savedFood -> updateExistingSavedFood(id, savedFoodRequest, bindingResult, model, redirectAttributes, savedFood))
                .orElseGet(() -> {
                    redirectAttributes.addFlashAttribute("successMessage", "Saved food not found.");
                    return "redirect:/foods";
                });
    }

    private String updateExistingSavedFood(
            Long id,
            SavedFoodRequest savedFoodRequest,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes,
            SavedFoodResponse savedFood
    ) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("savedFood", savedFood);
            return "food-edit";
        }

        savedFoodService.update(id, savedFoodRequest)
                .ifPresent(updatedSavedFood -> redirectAttributes.addFlashAttribute("successMessage", "Saved food updated."));
        return "redirect:/foods";
    }

    @PostMapping("/foods/{id}/deactivate")
    public String deactivateSavedFood(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        savedFoodService.deactivate(id);
        redirectAttributes.addFlashAttribute("successMessage", "Saved food deactivated.");
        return "redirect:/foods";
    }

    @PostMapping("/foods/{id}/reactivate")
    public String reactivateSavedFood(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        savedFoodService.reactivate(id);
        redirectAttributes.addFlashAttribute("successMessage", "Saved food reactivated.");
        return "redirect:/foods?includeInactive=true";
    }

    private void addFoodsModelAttributes(Model model, String query, boolean includeInactive) {
        model.addAttribute("savedFoods", savedFoodService.searchSavedFoods(query, includeInactive));
        model.addAttribute("searchQuery", query);
        model.addAttribute("includeInactive", includeInactive);
    }
}

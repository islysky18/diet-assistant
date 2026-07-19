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

import java.util.List;

@Controller
public class FoodEntryController {

    private final FoodEntryService foodEntryService;
    private final SavedFoodService savedFoodService;

    public FoodEntryController(FoodEntryService foodEntryService, SavedFoodService savedFoodService) {
        this.foodEntryService = foodEntryService;
        this.savedFoodService = savedFoodService;
    }

    @GetMapping("/food")
    public String showFood(@RequestParam(required = false) Long savedFoodId, Model model) {
        if (!model.containsAttribute("foodEntryRequest")) {
            model.addAttribute("foodEntryRequest", foodEntryService.newRequestForCurrentTime(savedFoodId));
        }
        addFoodModelAttributes(model);
        return "food";
    }

    @PostMapping("/food")
    public String createFoodEntry(
            @Valid @ModelAttribute("foodEntryRequest") FoodEntryRequest foodEntryRequest,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            addFoodModelAttributes(model);
            return "food";
        }
        foodEntryService.validateCreate(foodEntryRequest)
                .ifPresent(message -> bindingResult.reject("foodEntryRequest", message));
        if (bindingResult.hasErrors()) {
            addFoodModelAttributes(model);
            return "food";
        }

        foodEntryService.create(foodEntryRequest);
        redirectAttributes.addFlashAttribute("successMessage", "Food entry saved.");
        return "redirect:/food";
    }

    @PostMapping("/food/{id}/delete")
    public String deleteFoodEntry(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        foodEntryService.delete(id);
        redirectAttributes.addFlashAttribute("successMessage", "Food entry deleted.");
        return "redirect:/food";
    }

    private void addFoodModelAttributes(Model model) {
        List<FoodEntryResponse> foodEntries = foodEntryService.listTodayEntries();
        model.addAttribute("mealTypes", MealType.values());
        model.addAttribute("savedFoods", savedFoodService.listActiveSavedFoods());
        model.addAttribute("foodEntries", foodEntries);
        model.addAttribute("dailyTotals", foodEntryService.calculateTotals(foodEntries));
    }
}

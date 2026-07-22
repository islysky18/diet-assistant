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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
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
    public String showFood(
            @RequestParam(required = false) Long savedFoodId,
            @RequestParam(required = false) String date,
            Model model
    ) {
        LocalDate selectedDate = parseSelectedDate(date, model);
        if (!model.containsAttribute("foodEntryRequest")) {
            FoodEntryRequest request = foodEntryService.newRequestForCurrentTime(savedFoodId);
            request.setEatenAt(LocalDateTime.of(selectedDate, request.getEatenAt().toLocalTime()));
            model.addAttribute("foodEntryRequest", request);
        }
        addFoodModelAttributes(model, selectedDate);
        return "food";
    }

    @PostMapping("/food")
    public String createFoodEntry(
            @Valid @ModelAttribute("foodEntryRequest") FoodEntryRequest foodEntryRequest,
            BindingResult bindingResult,
            @RequestParam(required = false) String date,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        LocalDate selectedDate = requestDate(date, foodEntryRequest.getEatenAt());
        if (bindingResult.hasErrors()) {
            addFoodModelAttributes(model, selectedDate);
            return "food";
        }
        foodEntryService.validateCreate(foodEntryRequest)
                .ifPresent(message -> bindingResult.reject("foodEntryRequest", message));
        if (bindingResult.hasErrors()) {
            addFoodModelAttributes(model, selectedDate);
            return "food";
        }

        FoodEntryResponse created = foodEntryService.create(foodEntryRequest);
        redirectAttributes.addFlashAttribute("successMessage", "Food entry saved.");
        return redirectToDate(created.eatenAt().toLocalDate());
    }

    @GetMapping("/food/{id}/edit")
    public String editFoodEntry(@PathVariable Long id, Model model) {
        FoodEntryResponse foodEntry = foodEntryService.getForEdit(id);
        if (!model.containsAttribute("foodEntryEditRequest")) {
            model.addAttribute("foodEntryEditRequest", foodEntryService.toEditRequest(foodEntry));
        }
        addEditModelAttributes(model, foodEntry);
        return "food-entry-edit";
    }

    @PostMapping("/food/{id}")
    public String updateFoodEntry(
            @PathVariable Long id,
            @Valid @ModelAttribute("foodEntryEditRequest") FoodEntryEditRequest request,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        FoodEntryResponse foodEntry = foodEntryService.getForEdit(id);
        if (!bindingResult.hasErrors()) {
            foodEntryService.validateUpdate(id, request)
                    .ifPresent(error -> bindingResult.rejectValue(error.field(), "foodEntryEditRequest", error.message()));
        }
        if (bindingResult.hasErrors()) {
            addEditModelAttributes(model, foodEntry);
            return "food-entry-edit";
        }

        FoodEntryResponse updated = foodEntryService.update(id, request);
        redirectAttributes.addFlashAttribute("successMessage", "Food entry updated.");
        return redirectToDate(updated.eatenAt().toLocalDate());
    }

    @PostMapping("/food/{id}/delete")
    public String deleteFoodEntry(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        LocalDate entryDate = foodEntryService.getForEdit(id).eatenAt().toLocalDate();
        foodEntryService.delete(id);
        redirectAttributes.addFlashAttribute("successMessage", "Food entry deleted.");
        return redirectToDate(entryDate);
    }

    private void addFoodModelAttributes(Model model, LocalDate selectedDate) {
        List<FoodEntryResponse> foodEntries = foodEntryService.listEntriesForDate(selectedDate);
        LocalDate today = LocalDate.now();
        model.addAttribute("mealTypes", MealType.values());
        model.addAttribute("savedFoods", savedFoodService.listActiveSavedFoods());
        model.addAttribute("foodEntries", foodEntries);
        model.addAttribute("dailyTotals", foodEntryService.calculateTotals(foodEntries));
        model.addAttribute("selectedDate", selectedDate);
        model.addAttribute("previousDate", selectedDate.minusDays(1));
        model.addAttribute("nextDate", selectedDate.plusDays(1));
        model.addAttribute("today", today);
        model.addAttribute("isToday", selectedDate.equals(today));
    }

    private void addEditModelAttributes(Model model, FoodEntryResponse foodEntry) {
        model.addAttribute("foodEntry", foodEntry);
        model.addAttribute("mealTypes", MealType.values());
        model.addAttribute("returnDate", foodEntry.eatenAt().toLocalDate());
    }

    private LocalDate parseSelectedDate(String date, Model model) {
        if (date == null || date.isBlank()) {
            return LocalDate.now();
        }
        try {
            return LocalDate.parse(date);
        } catch (DateTimeParseException exception) {
            model.addAttribute("dateWarning", "The requested date was invalid. Showing today instead.");
            return LocalDate.now();
        }
    }

    private LocalDate requestDate(String date, LocalDateTime eatenAt) {
        if (date != null && !date.isBlank()) {
            try {
                return LocalDate.parse(date);
            } catch (DateTimeParseException ignored) {
                // Fall back to the submitted entry date.
            }
        }
        return eatenAt == null ? LocalDate.now() : eatenAt.toLocalDate();
    }

    private String redirectToDate(LocalDate date) {
        if (date.equals(LocalDate.now())) {
            return "redirect:/food";
        }
        return "redirect:/food?date=" + date;
    }
}

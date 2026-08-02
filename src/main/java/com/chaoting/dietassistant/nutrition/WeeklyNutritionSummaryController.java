package com.chaoting.dietassistant.nutrition;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

@Controller
public class WeeklyNutritionSummaryController {

    private final WeeklyNutritionSummaryService weeklyNutritionSummaryService;
    private final Clock clock;

    public WeeklyNutritionSummaryController(WeeklyNutritionSummaryService weeklyNutritionSummaryService, Clock clock) {
        this.weeklyNutritionSummaryService = weeklyNutritionSummaryService;
        this.clock = clock;
    }

    @GetMapping("/nutrition-summary")
    public String showSummary(@RequestParam(required = false) String weekStart, Model model) {
        LocalDate requestedDate = parseWeekStart(weekStart, model);
        WeeklyNutritionSummaryResponse summary = weeklyNutritionSummaryService.getSummary(requestedDate);
        if (requestedDate != null
                && weeklyNutritionSummaryService.normalizeWeekStart(requestedDate).isAfter(summary.currentWeekStart())) {
            model.addAttribute("weekWarning", "Future weeks are not available. Showing this week instead.");
        }
        model.addAttribute("summary", summary);
        model.addAttribute("recordFoodDate", summary.recordFoodDate(LocalDate.now(clock)));
        return "nutrition-summary";
    }

    private LocalDate parseWeekStart(String weekStart, Model model) {
        if (weekStart == null || weekStart.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(weekStart);
        } catch (DateTimeParseException exception) {
            model.addAttribute("weekWarning", "The requested week was invalid. Showing this week instead.");
            return null;
        }
    }
}

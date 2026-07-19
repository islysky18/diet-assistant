package com.chaoting.dietassistant.nutrition;

import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Optional;

@Controller
public class NutritionGoalController {

    private final NutritionGoalService nutritionGoalService;

    public NutritionGoalController(NutritionGoalService nutritionGoalService) {
        this.nutritionGoalService = nutritionGoalService;
    }

    @GetMapping("/nutrition-goals")
    public String showNutritionGoals(Model model) {
        Optional<NutritionGoalResponse> goal = nutritionGoalService.getCurrentGoal();
        if (!model.containsAttribute("nutritionGoalRequest")) {
            model.addAttribute("nutritionGoalRequest", nutritionGoalService.toRequest(goal));
        }
        model.addAttribute("hasNutritionGoal", goal.isPresent());
        return "nutrition-goals";
    }

    @PostMapping("/nutrition-goals")
    public String saveNutritionGoals(
            @Valid @ModelAttribute("nutritionGoalRequest") NutritionGoalRequest nutritionGoalRequest,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("hasNutritionGoal", nutritionGoalService.getCurrentGoal().isPresent());
            return "nutrition-goals";
        }

        nutritionGoalService.save(nutritionGoalRequest);
        redirectAttributes.addFlashAttribute("successMessage", "Nutrition goals saved.");
        return "redirect:/nutrition-goals";
    }
}

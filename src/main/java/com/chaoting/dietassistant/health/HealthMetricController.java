package com.chaoting.dietassistant.health;

import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class HealthMetricController {

    private final HealthMetricService healthMetricService;

    public HealthMetricController(HealthMetricService healthMetricService) {
        this.healthMetricService = healthMetricService;
    }

    @GetMapping("/health")
    public String showHealth(Model model) {
        if (!model.containsAttribute("healthMetricRequest")) {
            model.addAttribute("healthMetricRequest", new HealthMetricRequest());
        }
        addHealthModelAttributes(model);
        return "health";
    }

    @PostMapping("/health")
    public String createHealthMetric(
            @Valid @ModelAttribute("healthMetricRequest") HealthMetricRequest healthMetricRequest,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            addHealthModelAttributes(model);
            return "health";
        }

        healthMetricService.create(healthMetricRequest);
        redirectAttributes.addFlashAttribute("successMessage", "Health metric saved.");
        return "redirect:/health";
    }

    @PostMapping("/health/{id}/delete")
    public String deleteHealthMetric(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        healthMetricService.delete(id);
        redirectAttributes.addFlashAttribute("successMessage", "Health metric deleted.");
        return "redirect:/health";
    }

    private void addHealthModelAttributes(Model model) {
        model.addAttribute("metricTypes", MetricType.values());
        model.addAttribute("healthMetrics", healthMetricService.listMetrics());
    }
}

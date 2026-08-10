package com.chaoting.dietassistant.energy;

import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;

@Controller
public class DailyEnergyController {
    private final DailyEnergyService service;
    public DailyEnergyController(DailyEnergyService service) { this.service = service; }

    @GetMapping("/daily-energy")
    String form(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date, Model model) {
        if (!model.containsAttribute("energyRequest")) model.addAttribute("energyRequest", service.newManualRequest(date));
        return "daily-energy";
    }

    @PostMapping("/daily-energy")
    String save(@Valid @ModelAttribute("energyRequest") ManualEnergyRequest request, BindingResult result,
                Model model, RedirectAttributes redirect) {
        if (!result.hasErrors()) {
            try { service.saveManual(request); }
            catch (IllegalArgumentException ex) { result.reject("energy.invalid", ex.getMessage()); }
        }
        if (result.hasErrors()) return "daily-energy";
        redirect.addFlashAttribute("successMessage", "Daily energy saved.");
        return "redirect:/?date=" + request.getDate();
    }
}

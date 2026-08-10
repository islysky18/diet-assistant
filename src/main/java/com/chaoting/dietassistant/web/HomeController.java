package com.chaoting.dietassistant.web;

import com.chaoting.dietassistant.nutrition.DailyProgressService;
import com.chaoting.dietassistant.energy.DailyEnergyService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.Clock;
import java.time.LocalDate;

@Controller
class HomeController {

    private final DailyProgressService dailyProgressService;
    private final Clock clock;
    private final DailyEnergyService dailyEnergyService;

    HomeController(DailyProgressService dailyProgressService, Clock clock, DailyEnergyService dailyEnergyService) {
        this.dailyProgressService = dailyProgressService;
        this.clock = clock;
        this.dailyEnergyService = dailyEnergyService;
    }

    @GetMapping("/")
    String today(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate date,
            Model model
    ) {
        LocalDate selectedDate = date == null ? LocalDate.now(clock) : date;
        model.addAttribute("progress", dailyProgressService.getProgress(selectedDate));
        model.addAttribute("energy", dailyEnergyService.summary(selectedDate));
        return "today";
    }
}

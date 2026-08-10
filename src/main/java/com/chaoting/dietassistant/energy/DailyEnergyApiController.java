package com.chaoting.dietassistant.energy;

import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/health/daily-energy")
public class DailyEnergyApiController {
    private final DailyEnergyService service;
    public DailyEnergyApiController(DailyEnergyService service) { this.service = service; }

    @PutMapping("/{date}")
    ResponseEntity<EnergySyncResponse> sync(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @Valid @RequestBody AppleHealthEnergyRequest request) {
        return ResponseEntity.ok(service.syncAppleHealth(date, request));
    }

    @ExceptionHandler(StaleEnergyUpdateException.class)
    ResponseEntity<Map<String,String>> stale(StaleEnergyUpdateException exception) {
        return ResponseEntity.status(409).body(Map.of("error", exception.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Map<String,String>> invalid(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<Map<String,String>> missingProfile(IllegalStateException exception) {
        return ResponseEntity.status(404).body(Map.of("error", exception.getMessage()));
    }
}

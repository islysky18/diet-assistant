package com.chaoting.dietassistant.energy;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDate;

public class ManualEnergyRequest extends EnergyFields {
    @NotNull @PastOrPresent @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate date;
    public LocalDate getDate() { return date; }
    public void setDate(LocalDate value) { date = value; }
}

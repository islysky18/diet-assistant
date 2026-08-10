package com.chaoting.dietassistant.energy;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record EnergySyncResponse(LocalDate date, EnergySource source, boolean updated, OffsetDateTime sourceUpdatedAt) { }

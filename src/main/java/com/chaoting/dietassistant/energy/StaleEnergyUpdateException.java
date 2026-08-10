package com.chaoting.dietassistant.energy;

public class StaleEnergyUpdateException extends RuntimeException {
    public StaleEnergyUpdateException() { super("The sync is older than the stored Apple Health data."); }
}

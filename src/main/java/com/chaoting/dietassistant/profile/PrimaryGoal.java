package com.chaoting.dietassistant.profile;

public enum PrimaryGoal {
    LOSE_WEIGHT("Lose weight"),
    MAINTAIN_WEIGHT("Maintain weight"),
    GAIN_MUSCLE("Gain muscle"),
    IMPROVE_NUTRITION("Improve nutrition");

    private final String displayName;

    PrimaryGoal(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}

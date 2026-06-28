package com.chaoting.dietassistant.health;

public enum MetricType {
    HBA1C("HbA1c", "%"),
    LDL("LDL", "mg/dL"),
    HDL("HDL", "mg/dL"),
    TRIGLYCERIDES("Triglycerides", "mg/dL"),
    TOTAL_CHOLESTEROL("Total cholesterol", "mg/dL"),
    FASTING_GLUCOSE("Fasting glucose", "mg/dL"),
    SYSTOLIC_BLOOD_PRESSURE("Systolic blood pressure", "mmHg"),
    DIASTOLIC_BLOOD_PRESSURE("Diastolic blood pressure", "mmHg"),
    WAIST_CIRCUMFERENCE("Waist circumference", "cm"),
    WEIGHT("Weight", "kg");

    private final String displayName;
    private final String defaultUnit;

    MetricType(String displayName, String defaultUnit) {
        this.displayName = displayName;
        this.defaultUnit = defaultUnit;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDefaultUnit() {
        return defaultUnit;
    }
}

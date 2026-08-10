package com.chaoting.dietassistant.energy;

import jakarta.validation.constraints.Null;
import java.time.OffsetDateTime;

public class AppleHealthEnergyRequest extends EnergyFields {
    private OffsetDateTime sourceUpdatedAt;
    @Null(message = "profileId must not be supplied") private Object profileId;
    public OffsetDateTime getSourceUpdatedAt() { return sourceUpdatedAt; }
    public void setSourceUpdatedAt(OffsetDateTime value) { sourceUpdatedAt = value; }
    public Object getProfileId() { return profileId; }
    public void setProfileId(Object value) { profileId = value; }
}

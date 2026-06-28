package com.chaoting.dietassistant.health;

import com.chaoting.dietassistant.profile.ProfileResponse;
import com.chaoting.dietassistant.profile.CurrentProfileProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class HealthMetricService {

    private final HealthMetricRepository healthMetricRepository;
    private final CurrentProfileProvider currentProfileProvider;
    private final Clock clock;

    public HealthMetricService(
            HealthMetricRepository healthMetricRepository,
            CurrentProfileProvider currentProfileProvider,
            Clock clock
    ) {
        this.healthMetricRepository = healthMetricRepository;
        this.currentProfileProvider = currentProfileProvider;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<HealthMetricResponse> listMetrics() {
        return currentProfileProvider.getProfile()
                .map(profile -> healthMetricRepository.findByProfileIdOrderByMeasuredDateDescIdDesc(profile.id()).stream()
                        .map(this::toResponse)
                        .toList())
                .orElseGet(List::of);
    }

    @Transactional
    public HealthMetricResponse create(HealthMetricRequest request) {
        ProfileResponse profile = requireProfile();
        HealthMetric healthMetric = new HealthMetric();
        healthMetric.setProfileId(profile.id());
        healthMetric.setMetricType(request.getMetricType());
        healthMetric.setValue(request.getValue());
        healthMetric.setUnit(request.getUnit().trim());
        healthMetric.setMeasuredDate(request.getMeasuredDate());
        healthMetric.setNotes(blankToNull(request.getNotes()));
        healthMetric.setCreatedAt(LocalDateTime.now(clock));
        return toResponse(healthMetricRepository.save(healthMetric));
    }

    @Transactional
    public void delete(Long id) {
        ProfileResponse profile = requireProfile();
        healthMetricRepository.findById(id)
                .filter(healthMetric -> healthMetric.getProfileId().equals(profile.id()))
                .ifPresent(healthMetricRepository::delete);
    }

    private ProfileResponse requireProfile() {
        return currentProfileProvider.getProfile()
                .orElseThrow(() -> new IllegalStateException("Create the user profile before recording health metrics."));
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private HealthMetricResponse toResponse(HealthMetric healthMetric) {
        return new HealthMetricResponse(
                healthMetric.getId(),
                healthMetric.getMetricType(),
                healthMetric.getValue(),
                healthMetric.getUnit(),
                healthMetric.getMeasuredDate(),
                healthMetric.getNotes(),
                healthMetric.getCreatedAt()
        );
    }
}

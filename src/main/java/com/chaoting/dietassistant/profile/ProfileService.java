package com.chaoting.dietassistant.profile;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class ProfileService implements CurrentProfileProvider {

    private final ProfileRepository profileRepository;

    public ProfileService(ProfileRepository profileRepository) {
        this.profileRepository = profileRepository;
    }

    @Transactional(readOnly = true)
    @Override
    public Optional<ProfileResponse> getProfile() {
        return profileRepository.findFirstByOrderByIdAsc().map(this::toResponse);
    }

    @Transactional
    public ProfileResponse save(ProfileRequest request) {
        Profile profile = profileRepository.findFirstByOrderByIdAsc().orElseGet(Profile::new);
        applyRequest(profile, request);
        return toResponse(profileRepository.save(profile));
    }

    private void applyRequest(Profile profile, ProfileRequest request) {
        profile.setBirthYear(request.getBirthYear());
        profile.setHeightCm(request.getHeightCm());
        profile.setWeightKg(request.getWeightKg());
        profile.setPrimaryGoal(request.getPrimaryGoal());
        profile.setMealsPerDay(request.getMealsPerDay());
        profile.setWeekdayDinnerTime(request.getWeekdayDinnerTime());
        profile.setNotes(blankToNull(request.getNotes()));
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    ProfileRequest toRequest(ProfileResponse response) {
        ProfileRequest request = new ProfileRequest();
        request.setBirthYear(response.birthYear());
        request.setHeightCm(response.heightCm());
        request.setWeightKg(response.weightKg());
        request.setPrimaryGoal(response.primaryGoal());
        request.setMealsPerDay(response.mealsPerDay());
        request.setWeekdayDinnerTime(response.weekdayDinnerTime());
        request.setNotes(response.notes());
        return request;
    }

    private ProfileResponse toResponse(Profile profile) {
        return new ProfileResponse(
                profile.getId(),
                profile.getBirthYear(),
                profile.getHeightCm(),
                profile.getWeightKg(),
                profile.getPrimaryGoal(),
                profile.getMealsPerDay(),
                profile.getWeekdayDinnerTime(),
                profile.getNotes()
        );
    }
}

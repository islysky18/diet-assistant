package com.chaoting.dietassistant.profile;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ProfileServiceTest {

    @Test
    void saveCreatesProfileWhenNoneExists() {
        FakeProfileRepository profileRepository = new FakeProfileRepository(Optional.empty());
        ProfileService profileService = new ProfileService(profileRepository.proxy());
        ProfileRequest request = request(
                1988,
                new BigDecimal("175.50"),
                new BigDecimal("72.25"),
                PrimaryGoal.MAINTAIN_WEIGHT,
                3,
                LocalTime.of(18, 30),
                "  Mostly home-cooked meals.  "
        );

        ProfileResponse response = profileService.save(request);

        assertThat(response.birthYear()).isEqualTo(1988);
        assertThat(response.heightCm()).isEqualByComparingTo("175.50");
        assertThat(response.weightKg()).isEqualByComparingTo("72.25");
        assertThat(response.primaryGoal()).isEqualTo(PrimaryGoal.MAINTAIN_WEIGHT);
        assertThat(response.mealsPerDay()).isEqualTo(3);
        assertThat(response.weekdayDinnerTime()).isEqualTo(LocalTime.of(18, 30));
        assertThat(response.notes()).isEqualTo("Mostly home-cooked meals.");
        assertThat(profileRepository.savedProfile()).isNotNull();
    }

    @Test
    void saveUpdatesExistingProfile() {
        Profile existing = new Profile();
        existing.setBirthYear(1980);
        existing.setPrimaryGoal(PrimaryGoal.LOSE_WEIGHT);
        FakeProfileRepository profileRepository = new FakeProfileRepository(Optional.of(existing));
        ProfileService profileService = new ProfileService(profileRepository.proxy());

        ProfileRequest request = request(
                1991,
                new BigDecimal("168.00"),
                new BigDecimal("64.00"),
                PrimaryGoal.GAIN_MUSCLE,
                4,
                LocalTime.of(19, 0),
                "   "
        );

        ProfileResponse response = profileService.save(request);

        assertThat(response.birthYear()).isEqualTo(1991);
        assertThat(response.heightCm()).isEqualByComparingTo("168.00");
        assertThat(response.weightKg()).isEqualByComparingTo("64.00");
        assertThat(response.primaryGoal()).isEqualTo(PrimaryGoal.GAIN_MUSCLE);
        assertThat(response.mealsPerDay()).isEqualTo(4);
        assertThat(response.weekdayDinnerTime()).isEqualTo(LocalTime.of(19, 0));
        assertThat(response.notes()).isNull();
        assertThat(profileRepository.savedProfile()).isSameAs(existing);
    }

    private ProfileRequest request(
            Integer birthYear,
            BigDecimal heightCm,
            BigDecimal weightKg,
            PrimaryGoal primaryGoal,
            Integer mealsPerDay,
            LocalTime weekdayDinnerTime,
            String notes
    ) {
        ProfileRequest request = new ProfileRequest();
        request.setBirthYear(birthYear);
        request.setHeightCm(heightCm);
        request.setWeightKg(weightKg);
        request.setPrimaryGoal(primaryGoal);
        request.setMealsPerDay(mealsPerDay);
        request.setWeekdayDinnerTime(weekdayDinnerTime);
        request.setNotes(notes);
        return request;
    }

    private static class FakeProfileRepository {

        private final Optional<Profile> existingProfile;
        private Profile savedProfile;

        FakeProfileRepository(Optional<Profile> existingProfile) {
            this.existingProfile = existingProfile;
        }

        ProfileRepository proxy() {
            return (ProfileRepository) Proxy.newProxyInstance(
                    ProfileRepository.class.getClassLoader(),
                    new Class<?>[]{ProfileRepository.class},
                    (proxy, method, args) -> {
                        if (method.getName().equals("findFirstByOrderByIdAsc")) {
                            return existingProfile;
                        }
                        if (method.getName().equals("save")) {
                            savedProfile = (Profile) args[0];
                            return savedProfile;
                        }
                        if (method.getName().equals("toString")) {
                            return "FakeProfileRepository";
                        }
                        throw new UnsupportedOperationException(method.getName());
                    }
            );
        }

        Profile savedProfile() {
            return savedProfile;
        }
    }
}

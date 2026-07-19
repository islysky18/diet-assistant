package com.chaoting.dietassistant.nutrition;

import com.chaoting.dietassistant.profile.ProfileResponse;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NutritionGoalServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-07-18T18:30:00Z"),
            ZoneOffset.UTC
    );

    @Test
    void createsGoalOwnedByCurrentProfile() {
        FakeNutritionGoalRepository repository = new FakeNutritionGoalRepository();
        NutritionGoalService service = service(repository);

        NutritionGoalResponse response = service.save(request("2000", "120", "250", "70"));

        assertThat(repository.profileIdForLookup).isEqualTo(7L);
        assertThat(repository.savedGoal.getProfileId()).isEqualTo(7L);
        assertThat(response.dailyCalories()).isEqualByComparingTo("2000");
        assertThat(response.createdAt()).isEqualTo(LocalDateTime.of(2026, 7, 18, 18, 30));
        assertThat(response.updatedAt()).isEqualTo(LocalDateTime.of(2026, 7, 18, 18, 30));
    }

    @Test
    void updatesExistingGoalWithoutCreatingAnotherEntity() {
        FakeNutritionGoalRepository repository = new FakeNutritionGoalRepository();
        NutritionGoal existing = goal("1800", "100", "200", "60");
        existing.setProfileId(7L);
        existing.setCreatedAt(LocalDateTime.of(2026, 7, 1, 9, 0));
        existing.setUpdatedAt(LocalDateTime.of(2026, 7, 1, 9, 0));
        repository.currentGoal = Optional.of(existing);
        NutritionGoalService service = service(repository);

        NutritionGoalResponse response = service.save(request("2100", "130", "260", "75"));

        assertThat(repository.savedGoal).isSameAs(existing);
        assertThat(repository.saveCalls).isEqualTo(1);
        assertThat(response.dailyCalories()).isEqualByComparingTo("2100");
        assertThat(response.createdAt()).isEqualTo(LocalDateTime.of(2026, 7, 1, 9, 0));
        assertThat(response.updatedAt()).isEqualTo(LocalDateTime.of(2026, 7, 18, 18, 30));
    }

    @Test
    void readsOnlyGoalForCurrentProfile() {
        FakeNutritionGoalRepository repository = new FakeNutritionGoalRepository();
        NutritionGoal existing = goal("2000", "120", "250", "70");
        existing.setProfileId(7L);
        existing.setCreatedAt(LocalDateTime.of(2026, 7, 1, 9, 0));
        existing.setUpdatedAt(LocalDateTime.of(2026, 7, 1, 9, 0));
        repository.currentGoal = Optional.of(existing);

        Optional<NutritionGoalResponse> response = service(repository).getCurrentGoal();

        assertThat(repository.profileIdForLookup).isEqualTo(7L);
        assertThat(response).get().extracting(NutritionGoalResponse::profileId).isEqualTo(7L);
    }

    @Test
    void requestRejectsMissingNegativeAndOutOfScaleValues() {
        try (var validatorFactory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = validatorFactory.getValidator();
            NutritionGoalRequest request = request("-0.01", "123456789", "1.001", "70");
            request.setDailyFatGrams(null);

            var violations = validator.validate(request);

            assertThat(violations).extracting(violation -> violation.getPropertyPath().toString())
                    .containsExactlyInAnyOrder(
                            "dailyCalories",
                            "dailyProteinGrams",
                            "dailyCarbohydrateGrams",
                            "dailyFatGrams"
                    );
        }
    }

    @Test
    void saveRequiresCurrentProfile() {
        NutritionGoalService service = new NutritionGoalService(
                new FakeNutritionGoalRepository().proxy(),
                Optional::empty,
                FIXED_CLOCK
        );

        assertThatThrownBy(() -> service.save(request("2000", "120", "250", "70")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Create the user profile before setting nutrition goals.");
    }

    private NutritionGoalService service(FakeNutritionGoalRepository repository) {
        return new NutritionGoalService(
                repository.proxy(),
                () -> Optional.of(profile()),
                FIXED_CLOCK
        );
    }

    private NutritionGoalRequest request(String calories, String protein, String carbohydrate, String fat) {
        NutritionGoalRequest request = new NutritionGoalRequest();
        request.setDailyCalories(new BigDecimal(calories));
        request.setDailyProteinGrams(new BigDecimal(protein));
        request.setDailyCarbohydrateGrams(new BigDecimal(carbohydrate));
        request.setDailyFatGrams(new BigDecimal(fat));
        return request;
    }

    private NutritionGoal goal(String calories, String protein, String carbohydrate, String fat) {
        NutritionGoal goal = new NutritionGoal();
        goal.setDailyCalories(new BigDecimal(calories));
        goal.setDailyProteinGrams(new BigDecimal(protein));
        goal.setDailyCarbohydrateGrams(new BigDecimal(carbohydrate));
        goal.setDailyFatGrams(new BigDecimal(fat));
        return goal;
    }

    private ProfileResponse profile() {
        return new ProfileResponse(
                7L,
                1988,
                new BigDecimal("175.50"),
                new BigDecimal("72.25"),
                null,
                3,
                LocalTime.of(18, 30),
                null
        );
    }

    private static class FakeNutritionGoalRepository {

        private Optional<NutritionGoal> currentGoal = Optional.empty();
        private NutritionGoal savedGoal;
        private Long profileIdForLookup;
        private int saveCalls;

        NutritionGoalRepository proxy() {
            return (NutritionGoalRepository) Proxy.newProxyInstance(
                    NutritionGoalRepository.class.getClassLoader(),
                    new Class<?>[]{NutritionGoalRepository.class},
                    (proxy, method, args) -> {
                        if (method.getName().equals("findByProfileId")) {
                            profileIdForLookup = (Long) args[0];
                            return currentGoal;
                        }
                        if (method.getName().equals("save")) {
                            savedGoal = (NutritionGoal) args[0];
                            saveCalls++;
                            return savedGoal;
                        }
                        if (method.getName().equals("toString")) {
                            return "FakeNutritionGoalRepository";
                        }
                        throw new UnsupportedOperationException(method.getName());
                    }
            );
        }
    }
}

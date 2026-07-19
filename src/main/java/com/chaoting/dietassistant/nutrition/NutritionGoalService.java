package com.chaoting.dietassistant.nutrition;

import com.chaoting.dietassistant.profile.CurrentProfileProvider;
import com.chaoting.dietassistant.profile.ProfileResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class NutritionGoalService {

    private final NutritionGoalRepository nutritionGoalRepository;
    private final CurrentProfileProvider currentProfileProvider;
    private final Clock clock;

    public NutritionGoalService(
            NutritionGoalRepository nutritionGoalRepository,
            CurrentProfileProvider currentProfileProvider,
            Clock clock
    ) {
        this.nutritionGoalRepository = nutritionGoalRepository;
        this.currentProfileProvider = currentProfileProvider;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Optional<NutritionGoalResponse> getCurrentGoal() {
        return currentProfileProvider.getProfile()
                .flatMap(profile -> nutritionGoalRepository.findByProfileId(profile.id()))
                .map(this::toResponse);
    }

    @Transactional
    public NutritionGoalResponse save(NutritionGoalRequest request) {
        ProfileResponse profile = requireProfile();
        LocalDateTime now = LocalDateTime.now(clock);
        NutritionGoal goal = nutritionGoalRepository.findByProfileId(profile.id())
                .orElseGet(() -> {
                    NutritionGoal newGoal = new NutritionGoal();
                    newGoal.setProfileId(profile.id());
                    newGoal.setCreatedAt(now);
                    return newGoal;
                });

        goal.setDailyCalories(request.getDailyCalories());
        goal.setDailyProteinGrams(request.getDailyProteinGrams());
        goal.setDailyCarbohydrateGrams(request.getDailyCarbohydrateGrams());
        goal.setDailyFatGrams(request.getDailyFatGrams());
        goal.setUpdatedAt(now);
        return toResponse(nutritionGoalRepository.save(goal));
    }

    public NutritionGoalRequest toRequest(Optional<NutritionGoalResponse> goal) {
        NutritionGoalRequest request = new NutritionGoalRequest();
        goal.ifPresent(current -> {
            request.setDailyCalories(current.dailyCalories());
            request.setDailyProteinGrams(current.dailyProteinGrams());
            request.setDailyCarbohydrateGrams(current.dailyCarbohydrateGrams());
            request.setDailyFatGrams(current.dailyFatGrams());
        });
        return request;
    }

    private ProfileResponse requireProfile() {
        return currentProfileProvider.getProfile()
                .orElseThrow(() -> new IllegalStateException("Create the user profile before setting nutrition goals."));
    }

    private NutritionGoalResponse toResponse(NutritionGoal goal) {
        return new NutritionGoalResponse(
                goal.getId(),
                goal.getProfileId(),
                goal.getDailyCalories(),
                goal.getDailyProteinGrams(),
                goal.getDailyCarbohydrateGrams(),
                goal.getDailyFatGrams(),
                goal.getCreatedAt(),
                goal.getUpdatedAt()
        );
    }
}

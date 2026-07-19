package com.chaoting.dietassistant.nutrition;

import com.chaoting.dietassistant.food.DailyNutritionTotalsResponse;
import com.chaoting.dietassistant.food.FoodEntryResponse;
import com.chaoting.dietassistant.food.FoodEntryService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
public class DailyProgressService {

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final int PERCENTAGE_SCALE = 2;

    private final FoodEntryService foodEntryService;
    private final NutritionGoalService nutritionGoalService;

    public DailyProgressService(
            FoodEntryService foodEntryService,
            NutritionGoalService nutritionGoalService
    ) {
        this.foodEntryService = foodEntryService;
        this.nutritionGoalService = nutritionGoalService;
    }

    public DailyProgressResponse getProgress(LocalDate date) {
        List<FoodEntryResponse> entries = foodEntryService.listEntriesForDate(date);
        DailyNutritionTotalsResponse totals = foodEntryService.calculateTotals(entries);
        Optional<NutritionGoalResponse> goal = nutritionGoalService.getCurrentGoal();

        return new DailyProgressResponse(
                date,
                date.minusDays(1),
                date.plusDays(1),
                goal.isPresent(),
                progress(totals.calories(), goal.map(NutritionGoalResponse::dailyCalories)),
                progress(totals.proteinGrams(), goal.map(NutritionGoalResponse::dailyProteinGrams)),
                progress(totals.carbohydrateGrams(), goal.map(NutritionGoalResponse::dailyCarbohydrateGrams)),
                progress(totals.fatGrams(), goal.map(NutritionGoalResponse::dailyFatGrams)),
                entries
        );
    }

    NutrientProgressResponse progress(BigDecimal consumed, Optional<BigDecimal> goal) {
        if (goal.isEmpty()) {
            return new NutrientProgressResponse(consumed, null, null, null, null);
        }

        BigDecimal goalValue = goal.get();
        BigDecimal remaining = goalValue.subtract(consumed).max(BigDecimal.ZERO);
        BigDecimal over = consumed.subtract(goalValue).max(BigDecimal.ZERO);
        BigDecimal percentage = goalValue.signum() > 0
                ? consumed.multiply(ONE_HUNDRED).divide(goalValue, PERCENTAGE_SCALE, RoundingMode.HALF_UP)
                : null;
        return new NutrientProgressResponse(consumed, goalValue, remaining, over, percentage);
    }
}

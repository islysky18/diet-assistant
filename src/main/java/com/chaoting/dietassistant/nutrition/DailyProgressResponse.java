package com.chaoting.dietassistant.nutrition;

import com.chaoting.dietassistant.food.FoodEntryResponse;

import java.time.LocalDate;
import java.util.List;

public record DailyProgressResponse(
        LocalDate selectedDate,
        LocalDate previousDate,
        LocalDate nextDate,
        boolean hasNutritionGoal,
        NutrientProgressResponse calories,
        NutrientProgressResponse protein,
        NutrientProgressResponse carbohydrate,
        NutrientProgressResponse fat,
        List<FoodEntryResponse> foodEntries
) {
}

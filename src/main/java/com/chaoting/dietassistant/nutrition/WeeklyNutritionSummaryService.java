package com.chaoting.dietassistant.nutrition;

import com.chaoting.dietassistant.food.DailyNutritionTotalsResponse;
import com.chaoting.dietassistant.food.FoodEntryResponse;
import com.chaoting.dietassistant.food.FoodEntryService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class WeeklyNutritionSummaryService {

    private static final BigDecimal DAYS_IN_WEEK = new BigDecimal("7");
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final DateTimeFormatter MONTH_DAY = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH);
    private static final DateTimeFormatter MONTH_DAY_YEAR = DateTimeFormatter.ofPattern("MMM d, uuuu", Locale.ENGLISH);

    private final FoodEntryService foodEntryService;
    private final NutritionGoalService nutritionGoalService;
    private final Clock clock;

    public WeeklyNutritionSummaryService(
            FoodEntryService foodEntryService,
            NutritionGoalService nutritionGoalService,
            Clock clock
    ) {
        this.foodEntryService = foodEntryService;
        this.nutritionGoalService = nutritionGoalService;
        this.clock = clock;
    }

    public WeeklyNutritionSummaryResponse getSummary(LocalDate requestedDate) {
        LocalDate today = LocalDate.now(clock);
        LocalDate currentWeekStart = normalizeWeekStart(today);
        LocalDate weekStart = normalizeWeekStart(requestedDate == null ? today : requestedDate);
        if (weekStart.isAfter(currentWeekStart)) {
            weekStart = currentWeekStart;
        }
        LocalDate weekEnd = weekStart.plusDays(6);
        List<FoodEntryResponse> entries = foodEntryService.listEntriesForDateRange(weekStart, weekStart.plusWeeks(1));
        Optional<NutritionGoalResponse> goal = nutritionGoalService.getCurrentGoal();

        List<DailyNutritionSummaryResponse> days = new ArrayList<>(7);
        DailyNutritionTotalsResponse weeklyTotals = DailyNutritionTotalsResponse.zero();
        int daysLogged = 0;
        for (int offset = 0; offset < 7; offset++) {
            LocalDate date = weekStart.plusDays(offset);
            List<FoodEntryResponse> dailyEntries = entries.stream()
                    .filter(entry -> entry.eatenAt().toLocalDate().equals(date))
                    .toList();
            DailyNutritionTotalsResponse totals = foodEntryService.calculateTotals(dailyEntries);
            if (!dailyEntries.isEmpty()) {
                daysLogged++;
            }
            weeklyTotals = add(weeklyTotals, totals);
            days.add(new DailyNutritionSummaryResponse(
                    date,
                    date.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH),
                    totals.calories(),
                    totals.proteinGrams(),
                    totals.carbohydrateGrams(),
                    totals.fatGrams(),
                    !dailyEntries.isEmpty()
            ));
        }

        BigDecimal averageCalories = average(weeklyTotals.calories(), 0);
        List<WeeklyNutrientProgressResponse> progress = List.of(
                nutrient("Calories", averageCalories, goal.map(NutritionGoalResponse::dailyCalories), "kcal"),
                nutrient("Protein", average(weeklyTotals.proteinGrams(), 1), goal.map(NutritionGoalResponse::dailyProteinGrams), "g"),
                nutrient("Carbohydrates", average(weeklyTotals.carbohydrateGrams(), 1), goal.map(NutritionGoalResponse::dailyCarbohydrateGrams), "g"),
                nutrient("Fat", average(weeklyTotals.fatGrams(), 1), goal.map(NutritionGoalResponse::dailyFatGrams), "g")
        );

        boolean currentWeek = weekStart.equals(currentWeekStart);
        return new WeeklyNutritionSummaryResponse(
                weekStart,
                weekEnd,
                weekStart.minusWeeks(1),
                currentWeek ? null : weekStart.plusWeeks(1),
                currentWeekStart,
                currentWeek,
                formatDateRange(weekStart, weekEnd),
                daysLogged,
                entries.size(),
                weeklyTotals.calories().setScale(0, RoundingMode.HALF_UP),
                averageCalories,
                progress,
                List.copyOf(days)
        );
    }

    public LocalDate normalizeWeekStart(LocalDate date) {
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    private WeeklyNutrientProgressResponse nutrient(
            String name,
            BigDecimal average,
            Optional<BigDecimal> goal,
            String unit
    ) {
        BigDecimal goalValue = goal.filter(value -> value.signum() > 0).orElse(null);
        BigDecimal percentage = goalValue == null
                ? null
                : average.multiply(ONE_HUNDRED).divide(goalValue, 0, RoundingMode.HALF_UP);
        return new WeeklyNutrientProgressResponse(name, average, goalValue, percentage, unit);
    }

    private BigDecimal average(BigDecimal total, int scale) {
        return total.divide(DAYS_IN_WEEK, scale, RoundingMode.HALF_UP);
    }

    private DailyNutritionTotalsResponse add(
            DailyNutritionTotalsResponse left,
            DailyNutritionTotalsResponse right
    ) {
        return new DailyNutritionTotalsResponse(
                left.calories().add(right.calories()),
                left.proteinGrams().add(right.proteinGrams()),
                left.carbohydrateGrams().add(right.carbohydrateGrams()),
                left.fatGrams().add(right.fatGrams()),
                left.fiberGrams().add(right.fiberGrams())
        );
    }

    private String formatDateRange(LocalDate start, LocalDate end) {
        if (start.getYear() != end.getYear()) {
            return MONTH_DAY_YEAR.format(start) + "–" + MONTH_DAY_YEAR.format(end);
        }
        if (start.getMonth() != end.getMonth()) {
            return MONTH_DAY.format(start) + "–" + MONTH_DAY_YEAR.format(end);
        }
        return MONTH_DAY.format(start) + "–" + end.getDayOfMonth() + ", " + end.getYear();
    }
}

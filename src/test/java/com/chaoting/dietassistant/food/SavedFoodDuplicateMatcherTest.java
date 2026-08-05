package com.chaoting.dietassistant.food;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class SavedFoodDuplicateMatcherTest {

    private final SavedFoodDuplicateMatcher matcher = new SavedFoodDuplicateMatcher();

    @Test
    void normalizesCaseWhitespacePunctuationAndDecimalScaleAndIgnoresNotes() {
        SavedFoodRequest request = request("  DIET   COKE ", " Coca-Cola ");
        request.setReferenceAmount(new BigDecimal("1.0"));
        request.setCalories(new BigDecimal("0.00"));
        request.setNotes("recognition run one");

        assertThat(matcher.matches(request, existing("Diet Coke", "Coca Cola", "1.00", "0", null)))
                .isTrue();
    }

    @Test
    void nullMatchesNullButDoesNotMatchExplicitZero() {
        SavedFoodRequest request = request("Diet Coke", null);
        request.setCalories(null);

        assertThat(matcher.matches(request, existing("Diet Coke", null, "1", null, null))).isTrue();
        assertThat(matcher.matches(request, existing("Diet Coke", "   ", "1", null, null))).isTrue();
        assertThat(matcher.matches(request, existing("Diet Coke", null, "1", "0", null))).isFalse();
    }

    @Test
    void everyIdentityFieldMustMatchExactlyAfterNormalization() {
        SavedFoodRequest request = request("Diet Coke", "Coca-Cola");

        assertThat(matcher.matches(request, existing("Diet Coke Lime", "Coca-Cola", "1", null, null))).isFalse();
        assertThat(matcher.matches(request, existing("Diet Coke", "Other", "1", null, null))).isFalse();
        assertThat(matcher.matches(request, existing("Diet Coke", "Coca-Cola", "2", null, null))).isFalse();
        assertThat(matcher.matches(request, response("Diet Coke", "Coca-Cola", "1", "bottle", null, null))).isFalse();
        assertThat(matcher.matches(request, existing("Popchips Sea Salt", "Coca-Cola", "1", null, null))).isFalse();
        SavedFoodRequest popchips = request("Popchips BBQ", "Popchips");
        assertThat(matcher.matches(popchips, existing("Popchips Sea Salt", "Popchips", "1", null, null))).isFalse();
    }

    @ParameterizedTest
    @EnumSource(NutritionField.class)
    void everyNutritionFieldMustMatchWithoutTolerance(NutritionField field) {
        SavedFoodRequest request = fullySpecifiedRequest();
        SavedFoodResponse existing = fullySpecifiedResponse(field);

        assertThat(matcher.matches(request, existing)).isFalse();
    }

    @Test
    void scaleOnlyDifferencesMatchAcrossEveryNumericField() {
        assertThat(matcher.matches(fullySpecifiedRequest(), new SavedFoodResponse(
                1L, "Diet Coke", "Coca-Cola", new BigDecimal("1.000"), "can",
                new BigDecimal("355.000"), new BigDecimal("0.000"), new BigDecimal("1.000"),
                new BigDecimal("2.000"), new BigDecimal("3.000"), new BigDecimal("4.000"),
                "different notes", true, LocalDateTime.MIN, LocalDateTime.MIN))).isTrue();
    }

    @Test
    void ignoresReferenceWeightThatCreationWouldDiscardForWeightUnits() {
        SavedFoodRequest request = request("Rice", null);
        request.setReferenceUnit(" Grams ");
        request.setReferenceWeightGrams(new BigDecimal("100"));

        assertThat(matcher.matches(request,
                response("Rice", null, "1.00", "g", null, null))).isTrue();
    }

    private SavedFoodRequest request(String name, String brand) {
        SavedFoodRequest request = new SavedFoodRequest();
        request.setName(name); request.setBrand(brand); request.setReferenceAmount(BigDecimal.ONE);
        request.setReferenceUnit("can");
        return request;
    }

    private SavedFoodRequest fullySpecifiedRequest() {
        SavedFoodRequest request = request("Diet Coke", "Coca-Cola");
        request.setReferenceWeightGrams(new BigDecimal("355"));
        request.setCalories(BigDecimal.ZERO);
        request.setProteinGrams(BigDecimal.ONE);
        request.setCarbohydrateGrams(new BigDecimal("2"));
        request.setFatGrams(new BigDecimal("3"));
        request.setFiberGrams(new BigDecimal("4"));
        return request;
    }

    private SavedFoodResponse fullySpecifiedResponse(NutritionField different) {
        BigDecimal weight = new BigDecimal("355");
        BigDecimal calories = BigDecimal.ZERO;
        BigDecimal protein = BigDecimal.ONE;
        BigDecimal carbs = new BigDecimal("2");
        BigDecimal fat = new BigDecimal("3");
        BigDecimal fiber = new BigDecimal("4");
        return new SavedFoodResponse(1L, "Diet Coke", "Coca-Cola", BigDecimal.ONE, "can",
                different == NutritionField.WEIGHT ? weight.add(new BigDecimal("0.01")) : weight,
                different == NutritionField.CALORIES ? calories.add(new BigDecimal("0.01")) : calories,
                different == NutritionField.PROTEIN ? protein.add(new BigDecimal("0.01")) : protein,
                different == NutritionField.CARBOHYDRATES ? carbs.add(new BigDecimal("0.01")) : carbs,
                different == NutritionField.FAT ? fat.add(new BigDecimal("0.01")) : fat,
                different == NutritionField.FIBER ? fiber.add(new BigDecimal("0.01")) : fiber,
                null, true, LocalDateTime.MIN, LocalDateTime.MIN);
    }

    private enum NutritionField { WEIGHT, CALORIES, PROTEIN, CARBOHYDRATES, FAT, FIBER }

    private SavedFoodResponse existing(String name, String brand, String amount, String calories, String notes) {
        return response(name, brand, amount, "can", calories, notes);
    }

    private SavedFoodResponse response(String name, String brand, String amount, String unit, String calories, String notes) {
        return new SavedFoodResponse(1L, name, brand, new BigDecimal(amount), unit, null,
                calories == null ? null : new BigDecimal(calories), null, null, null, null, notes, true,
                LocalDateTime.MIN, LocalDateTime.MIN);
    }
}

package com.chaoting.dietassistant.food;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
class UsdaFoodImportServiceTest {

    private final UsdaFoodImportService service = new UsdaFoodImportService(null);
    private final JsonMapper json = new JsonMapper();

    @Test
    void mapsGenericFoodOnUnscaledHundredGramBasisAndLeavesMissingNutrientNull() throws Exception {
        var request = service.mapDetails(json.readTree("""
                {"fdcId":123,"description":"Sweet potato, cooked","dataType":"Foundation","foodNutrients":[
                  {"nutrient":{"id":1008,"name":"Energy","unitName":"kcal"},"amount":90},
                  {"nutrient":{"id":1003,"name":"Protein","unitName":"g"},"amount":2.01},
                  {"nutrient":{"id":1005,"name":"Carbohydrate, by difference","unitName":"g"},"amount":20.71},
                  {"nutrient":{"id":1004,"name":"Total lipid (fat)","unitName":"g"},"amount":0.15}
                ]}
                """));

        assertThat(request.getName()).isEqualTo("Sweet potato, cooked");
        assertThat(request.getReferenceAmount()).isEqualByComparingTo("100");
        assertThat(request.getReferenceUnit()).isEqualTo("g");
        assertThat(request.getReferenceWeightGrams()).isNull();
        assertThat(request.getCalories()).isEqualByComparingTo("90");
        assertThat(request.getProteinGrams()).isEqualByComparingTo("2.01");
        assertThat(request.getFiberGrams()).isNull();
        assertThat(request.getNotes()).contains("FDC ID: 123");
    }

    @Test
    void caloriesUseKcalAndNeverKilojoules() throws Exception {
        var request = service.mapDetails(json.readTree("""
                {"fdcId":124,"description":"Rice","foodNutrients":[
                  {"nutrient":{"id":1008,"name":"Energy","unitName":"kJ"},"amount":544},
                  {"nutrient":{"id":1008,"name":"Energy","unitName":"kcal"},"amount":130}
                ]}
                """));
        assertThat(request.getCalories()).isEqualByComparingTo(new BigDecimal("130"));
    }

    @Test
    void brandedFoodMapsBrandButUsesConsistentHundredGramBasis() throws Exception {
        var request = service.mapDetails(json.readTree("""
                {"fdcId":125,"description":"Yogurt","brandOwner":"Example Dairy","servingSize":170,
                 "servingSizeUnit":"g","foodNutrients":[]}
                """));
        assertThat(request.getBrand()).isEqualTo("Example Dairy");
        assertThat(request.getReferenceAmount()).isEqualByComparingTo("100");
        assertThat(request.getReferenceUnit()).isEqualTo("g");
    }
}

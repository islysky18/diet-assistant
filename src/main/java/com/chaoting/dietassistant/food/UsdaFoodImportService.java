package com.chaoting.dietassistant.food;

import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;

@Service
public class UsdaFoodImportService {

    private final UsdaFoodDataClient client;

    public UsdaFoodImportService(UsdaFoodDataClient client) {
        this.client = client;
    }

    public boolean isConfigured() {
        return client.isConfigured();
    }

    public List<UsdaFoodSearchResult> search(String query) {
        if (query == null || query.isBlank()) return List.of();
        return client.search(query.trim());
    }

    public SavedFoodRequest prepareImport(long fdcId) {
        return mapDetails(client.details(fdcId));
    }

    SavedFoodRequest mapDetails(JsonNode food) {
        long fdcId = food.path("fdcId").asLong();
        SavedFoodRequest request = new SavedFoodRequest();
        request.setName(UsdaFoodDataClient.text(food, "description"));
        request.setBrand(UsdaFoodDataClient.firstText(food, "brandOwner", "brandName"));
        request.setReferenceAmount(new BigDecimal("100"));
        request.setReferenceUnit("g");
        request.setReferenceWeightGrams(null);
        request.setCalories(nutrient(food, "kcal", 1008, "energy"));
        request.setProteinGrams(nutrient(food, "g", 1003, "protein"));
        request.setCarbohydrateGrams(nutrient(food, "g", 1005, "carbohydrate"));
        request.setFatGrams(nutrient(food, "g", 1004, "total lipid", "total fat"));
        request.setFiberGrams(nutrient(food, "g", 1079, "fiber"));
        request.setNotes("Source: USDA FoodData Central\nFDC ID: " + fdcId);
        return request;
    }

    private BigDecimal nutrient(JsonNode food, String expectedUnit, int nutrientNumber, String... names) {
        JsonNode nutrients = food.get("foodNutrients");
        if (nutrients == null || !nutrients.isArray()) return null;
        for (JsonNode entry : nutrients) {
            JsonNode nutrient = entry.path("nutrient");
            String unit = UsdaFoodDataClient.text(nutrient, "unitName");
            String name = UsdaFoodDataClient.text(nutrient, "name");
            int number = nutrient.path("id").asInt(0);
            if (!expectedUnit.equalsIgnoreCase(unit == null ? "" : unit)) continue;
            boolean matches = number == nutrientNumber;
            if (!matches && name != null) {
                String normalized = name.toLowerCase(Locale.ROOT);
                for (String candidate : names) matches |= normalized.contains(candidate);
            }
            if (matches) {
                BigDecimal amount = UsdaFoodDataClient.decimal(entry, "amount");
                return amount == null ? null : amount.setScale(Math.min(2, Math.max(0, amount.scale())), RoundingMode.HALF_UP);
            }
        }
        return null;
    }
}

package com.chaoting.dietassistant.food;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import tools.jackson.databind.JsonNode;

import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Component
public class UsdaFoodDataClient {

    private static final String USER_ERROR = "USDA FoodData Central is temporarily unavailable. Please try again later.";
    private final RestClient restClient;
    private final String apiKey;

    @Autowired
    public UsdaFoodDataClient(@Value("${diet-assistant.usda.base-url:https://api.nal.usda.gov/fdc/v1}") String baseUrl,
                              @Value("${diet-assistant.usda.api-key:}") String apiKey) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(5000);
        requestFactory.setReadTimeout(10000);
        this.restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
        this.apiKey = apiKey == null ? "" : apiKey.trim();
    }

    UsdaFoodDataClient(RestClient restClient, String apiKey) {
        this.restClient = restClient;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
    }

    public boolean isConfigured() {
        return !apiKey.isEmpty();
    }

    public List<UsdaFoodSearchResult> search(String query) {
        requireConfigured();
        JsonNode root = get(uriBuilder -> uriBuilder.path("/foods/search")
                .queryParam("api_key", apiKey)
                .queryParam("query", query)
                .queryParam("pageSize", 25)
                .build());
        if (root == null) throw new UsdaFoodDataException(USER_ERROR);
        JsonNode foods = root.get("foods");
        if (foods == null || !foods.isArray()) {
            throw new UsdaFoodDataException(USER_ERROR);
        }
        List<UsdaFoodSearchResult> results = new ArrayList<>();
        for (JsonNode food : foods) {
            long id = food.path("fdcId").asLong(0);
            String description = text(food, "description");
            if (id <= 0 || description == null) continue;
            String brand = firstText(food, "brandOwner", "brandName");
            results.add(new UsdaFoodSearchResult(id, description, text(food, "dataType"), brand,
                    decimal(food, "servingSize"), text(food, "servingSizeUnit")));
        }
        results.sort(Comparator.comparingInt(result -> dataTypePriority(result.dataType())));
        return List.copyOf(results);
    }

    public JsonNode details(long fdcId) {
        requireConfigured();
        if (fdcId <= 0) throw new IllegalArgumentException("FDC ID must be a positive number.");
        JsonNode details = get(uriBuilder -> uriBuilder.path("/food/{fdcId}")
                .queryParam("api_key", apiKey)
                .build(fdcId));
        if (details == null) throw new UsdaFoodDataException(USER_ERROR);
        if (details.path("fdcId").asLong(0) <= 0 || text(details, "description") == null) {
            throw new UsdaFoodDataException(USER_ERROR);
        }
        return details;
    }

    private JsonNode get(java.util.function.Function<org.springframework.web.util.UriBuilder, URI> uri) {
        try {
            return restClient.get().uri(uri).retrieve()
                    .onStatus(HttpStatusCode::isError, (request, response) -> {
                        throw new UsdaFoodDataException(response.getStatusCode().value() == 429
                                ? "USDA FoodData Central rate limit reached. Please try again later." : USER_ERROR);
                    }).body(JsonNode.class);
        } catch (UsdaFoodDataException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new UsdaFoodDataException(USER_ERROR);
        }
    }

    private void requireConfigured() {
        if (!isConfigured()) {
            throw new UsdaFoodDataException("USDA food search is not configured. Set USDA_FDC_API_KEY to enable it.");
        }
    }

    private static int dataTypePriority(String dataType) {
        if (dataType == null) return 2;
        return switch (dataType) {
            case "Foundation", "SR Legacy", "Survey (FNDDS)" -> 0;
            case "Branded" -> 2;
            default -> 1;
        };
    }

    static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() || value.asString().isBlank() ? null : value.asString();
    }

    static String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = text(node, field);
            if (value != null) return value;
        }
        return null;
    }

    static java.math.BigDecimal decimal(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || !value.isNumber()) return null;
        return value.decimalValue();
    }
}

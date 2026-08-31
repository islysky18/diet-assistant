package com.chaoting.dietassistant.food;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class UsdaFoodDataClientTest {

    @Test
    void mapsSearchResponseAndRanksGenericFoodsBeforeBranded() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://example.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://example.test/foods/search?api_key=test-key&query=sweet%20potato&pageSize=25"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"foods":[
                         {"fdcId":2,"description":"Branded fries","dataType":"Branded","brandOwner":"Brand","servingSize":85,"servingSizeUnit":"g"},
                         {"fdcId":1,"description":"Sweet potato","dataType":"Foundation"}]}
                        """, MediaType.APPLICATION_JSON));
        UsdaFoodDataClient client = new UsdaFoodDataClient(builder.build(), "test-key");

        var results = client.search("sweet potato");

        assertThat(results).extracting(UsdaFoodSearchResult::fdcId).containsExactly(1L, 2L);
        assertThat(results.get(1).brand()).isEqualTo("Brand");
        server.verify();
    }

    @Test
    void missingKeyFailsWithoutMakingARequest() {
        UsdaFoodDataClient client = new UsdaFoodDataClient(RestClient.create(), "  ");
        assertThatThrownBy(() -> client.search("banana"))
                .isInstanceOf(UsdaFoodDataException.class)
                .hasMessageContaining("USDA_FDC_API_KEY");
    }

    @Test
    void upstreamErrorIsFriendlyAndDoesNotExposeApiKey() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://example.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(method(HttpMethod.GET)).andRespond(withResourceNotFound());
        String secret = "never-show-this-key";
        UsdaFoodDataClient client = new UsdaFoodDataClient(builder.build(), secret);

        assertThatThrownBy(() -> client.search("banana"))
                .isInstanceOf(UsdaFoodDataException.class)
                .hasMessage("USDA FoodData Central is temporarily unavailable. Please try again later.")
                .hasMessageNotContaining(secret);
        server.verify();
    }
}

package com.chaoting.dietassistant.food;

import com.chaoting.dietassistant.profile.CurrentProfileProvider;
import com.chaoting.dietassistant.profile.ProfileResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = {com.chaoting.dietassistant.DietAssistantApplication.class,
                UsdaFoodSearchIntegrationTest.Configuration.class})
@Testcontainers
class UsdaFoodSearchIntegrationTest {

    @Container
    @ServiceConnection
    static final MySQLContainer<?> mysql = new MySQLContainer<>(DockerImageName.parse("mysql:8.4"));

    @LocalServerPort
    private int port;

    @Autowired
    private FakeUsdaFoodImportService usdaService;

    private HttpClient httpClient;

    @BeforeEach
    void setUp() {
        usdaService.mode = Mode.RESULTS;
        httpClient = HttpClient.newHttpClient();
    }

    @Test
    void initialPageProcessesThymeleafWithSearchNotPerformed() throws Exception {
        HttpResponse<String> response = get("/foods/usda");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("Search for a food to see FoodData Central results.");
        assertThat(response.body()).doesNotContain("No USDA foods matched your search.");
    }

    @Test
    void submittedSearchProcessesThymeleafAndRendersResults() throws Exception {
        HttpResponse<String> response = search("sweet potato");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("Sweet potato, cooked", "FDC ID 123");
    }

    @Test
    void noResultsProcessesThymeleafAndRendersEmptyState() throws Exception {
        usdaService.mode = Mode.EMPTY;

        HttpResponse<String> response = search("missing food");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("No USDA foods matched your search.");
    }

    @Test
    void upstreamErrorProcessesThymeleafAndRendersOnlyErrorState() throws Exception {
        usdaService.mode = Mode.ERROR;

        HttpResponse<String> response = search("sweet potato");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("USDA FoodData Central is temporarily unavailable.");
        assertThat(response.body()).doesNotContain("No USDA foods matched your search.");
    }

    private HttpResponse<String> search(String query) throws IOException, InterruptedException {
        return get("/foods/usda?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8));
    }

    private HttpResponse<String> get(String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET().build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    @TestConfiguration
    static class Configuration {
        @Bean
        @Primary
        FakeUsdaFoodImportService fakeUsdaFoodImportService() {
            return new FakeUsdaFoodImportService();
        }

        @Bean
        @Primary
        CurrentProfileProvider testCurrentProfileProvider() {
            return () -> Optional.of(new ProfileResponse(1L, null, null, null, null, null, null, null));
        }
    }

    static final class FakeUsdaFoodImportService extends UsdaFoodImportService {
        private Mode mode = Mode.RESULTS;

        private FakeUsdaFoodImportService() {
            super(null);
        }

        @Override
        public boolean isConfigured() {
            return true;
        }

        @Override
        public List<UsdaFoodSearchResult> search(String query) {
            return switch (mode) {
                case RESULTS -> List.of(new UsdaFoodSearchResult(
                        123, "Sweet potato, cooked", "Foundation", null, null, null));
                case EMPTY -> List.of();
                case ERROR -> throw new UsdaFoodDataException(
                        "USDA FoodData Central is temporarily unavailable. Please try again later.");
            };
        }
    }

    private enum Mode { RESULTS, EMPTY, ERROR }
}

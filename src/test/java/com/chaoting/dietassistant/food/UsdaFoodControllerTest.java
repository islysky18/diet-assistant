package com.chaoting.dietassistant.food;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class UsdaFoodControllerTest {

    private UsdaFoodImportService usda;
    private SavedFoodService savedFoods;
    private SavedFoodDuplicateReviewService duplicates;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        usda = mock(UsdaFoodImportService.class);
        savedFoods = mock(SavedFoodService.class);
        duplicates = mock(SavedFoodDuplicateReviewService.class);
        when(savedFoods.hasCurrentProfile()).thenReturn(true);
        when(usda.isConfigured()).thenReturn(true);
        mvc = MockMvcBuilders.standaloneSetup(new UsdaFoodController(usda, savedFoods, duplicates)).build();
    }

    @Test
    void getSearchPageAndSubmitQueryDisplayResults() throws Exception {
        mvc.perform(get("/foods/usda")).andExpect(status().isOk())
                .andExpect(view().name("food-usda-search"))
                .andExpect(model().attribute("searchPerformed", false));
        when(usda.search("banana")).thenReturn(List.of(
                new UsdaFoodSearchResult(10, "Bananas, raw", "Foundation", null, null, null)));
        mvc.perform(get("/foods/usda").param("q", "banana"))
                .andExpect(status().isOk()).andExpect(model().attribute("searchQuery", "banana"))
                .andExpect(model().attribute("searchPerformed", true))
                .andExpect(model().attributeExists("searchResults"));
    }

    @Test
    void selectingItemRetrievesDetailsForPreview() throws Exception {
        SavedFoodRequest request = validRequest();
        when(usda.prepareImport(10)).thenReturn(request);
        mvc.perform(get("/foods/usda/10")).andExpect(status().isOk())
                .andExpect(view().name("food-usda-import"))
                .andExpect(model().attribute("savedFoodRequest", request));
        verify(usda).prepareImport(10);
    }

    @Test
    void finalPostUsesDuplicateReviewThenSavedFoodFlow() throws Exception {
        when(duplicates.beginUsda(any(), any())).thenReturn(Optional.empty());
        mvc.perform(post("/foods/usda/10")
                        .param("name", "Banana").param("referenceAmount", "100")
                        .param("referenceUnit", "g").param("calories", "89"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/foods"))
                .andExpect(flash().attribute("successMessage", "USDA food imported."));
        verify(duplicates).beginUsda(any(), any());
        verify(savedFoods).create(any());
    }

    @Test
    void duplicateRedirectsToExistingReview() throws Exception {
        when(duplicates.beginUsda(any(), any())).thenReturn(Optional.of("token"));
        mvc.perform(post("/foods/usda/10")
                        .param("name", "Banana").param("referenceAmount", "100").param("referenceUnit", "g"))
                .andExpect(redirectedUrl("/foods/duplicates/token"));
    }

    private SavedFoodRequest validRequest() {
        SavedFoodRequest request = new SavedFoodRequest();
        request.setName("Banana"); request.setReferenceAmount(new BigDecimal("100")); request.setReferenceUnit("g");
        return request;
    }
}

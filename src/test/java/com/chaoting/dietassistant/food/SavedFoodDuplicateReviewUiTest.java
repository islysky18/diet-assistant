package com.chaoting.dietassistant.food;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class SavedFoodDuplicateReviewUiTest {

    @Test
    void reviewShowsComparisonSafetyExplanationAndThreeExplicitActions() throws IOException {
        String template;
        try (var input = getClass().getResourceAsStream("/templates/food-duplicate-review.html")) {
            if (input == null) throw new IOException("food-duplicate-review.html not found");
            template = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertThat(template).contains(
                "Possible duplicate found",
                "Nothing has been created or changed",
                "The existing item will not be modified automatically",
                "Reference weight", "Calories", "Protein", "Carbohydrates", "Fat", "Fiber",
                "Use existing ", "Create this Saved Food anyway",
                "Back to photo review", "Back to Saved Food form",
                "role=\"status\""
        );
    }
}

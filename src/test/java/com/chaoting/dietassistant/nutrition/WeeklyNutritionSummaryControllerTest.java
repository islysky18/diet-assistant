package com.chaoting.dietassistant.nutrition;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ConcurrentModel;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WeeklyNutritionSummaryControllerTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-26T18:00:00Z"), ZoneOffset.UTC);
    private StubSummaryService service;
    private WeeklyNutritionSummaryController controller;

    @BeforeEach
    void setUp() {
        service = new StubSummaryService();
        controller = new WeeklyNutritionSummaryController(service, CLOCK);
    }

    @Test
    void missingWeekStartDefaultsToCurrentWeek() {
        ConcurrentModel model = new ConcurrentModel();

        String view = controller.showSummary(null, model);

        assertThat(view).isEqualTo("nutrition-summary");
        assertThat(service.requestedDate).isNull();
        assertThat(model.getAttribute("summary")).isSameAs(service.summary);
        assertThat(model.getAttribute("recordFoodDate")).isEqualTo(LocalDate.of(2026, 7, 26));
    }

    @Test
    void invalidWeekStartDoesNotThrowAndShowsWarning() {
        ConcurrentModel model = new ConcurrentModel();

        controller.showSummary("not-a-date", model);

        assertThat(service.requestedDate).isNull();
        assertThat(model.getAttribute("weekWarning")).isEqualTo(
                "The requested week was invalid. Showing this week instead."
        );
    }

    @Test
    void futureWeekShowsCurrentWeekWithWarning() {
        ConcurrentModel model = new ConcurrentModel();

        controller.showSummary("2026-08-03", model);

        assertThat(service.requestedDate).isEqualTo(LocalDate.of(2026, 8, 3));
        assertThat(model.getAttribute("weekWarning")).isEqualTo(
                "Future weeks are not available. Showing this week instead."
        );
    }

    private static final class StubSummaryService extends WeeklyNutritionSummaryService {
        private LocalDate requestedDate;
        private final WeeklyNutritionSummaryResponse summary = new WeeklyNutritionSummaryResponse(
                LocalDate.of(2026, 7, 20),
                LocalDate.of(2026, 7, 26),
                LocalDate.of(2026, 7, 13),
                null,
                LocalDate.of(2026, 7, 20),
                true,
                "Jul 20–26, 2026",
                0,
                0,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                List.of(),
                List.of()
        );

        private StubSummaryService() {
            super(null, null, CLOCK);
        }

        @Override
        public WeeklyNutritionSummaryResponse getSummary(LocalDate requestedDate) {
            this.requestedDate = requestedDate;
            return summary;
        }
    }
}

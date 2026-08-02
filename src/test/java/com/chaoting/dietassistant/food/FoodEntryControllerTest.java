package com.chaoting.dietassistant.food;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ConcurrentModel;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class FoodEntryControllerTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-01T18:00:00Z"), ZoneOffset.UTC);

    private StubFoodEntryService foodEntryService;
    private FoodEntryController controller;

    @BeforeEach
    void setUp() {
        foodEntryService = new StubFoodEntryService();
        controller = new FoodEntryController(foodEntryService, new StubSavedFoodService(), CLOCK);
    }

    @Test
    void historicalPageDefaultsCreateEatenAtToSelectedDateAndCurrentTime() {
        FoodEntryRequest request = new FoodEntryRequest();
        request.setEatenAt(LocalDateTime.of(2026, 8, 1, 11, 0));
        foodEntryService.newRequest = request;
        ConcurrentModel model = new ConcurrentModel();

        String view = controller.showFood(null, "2026-07-31", model);

        assertThat(view).isEqualTo("food");
        assertThat(((FoodEntryRequest) model.getAttribute("foodEntryRequest")).getEatenAt())
                .isEqualTo(LocalDateTime.of(2026, 7, 31, 11, 0));
        assertThat(model.getAttribute("today")).isEqualTo(LocalDate.of(2026, 8, 1));
    }

    @Test
    void createRedirectsToSubmittedEatenDateEvenWhenSelectedDateDiffers() {
        FoodEntryRequest request = new FoodEntryRequest();
        request.setEatenAt(LocalDateTime.of(2026, 7, 30, 20, 0));
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(request, "foodEntryRequest");
        foodEntryService.createdResponse = response(1L, request.getEatenAt());

        String redirect = controller.createFoodEntry(
                request,
                bindingResult,
                "2026-07-31",
                new ConcurrentModel(),
                new RedirectAttributesModelMap()
        );

        assertThat(redirect).isEqualTo("redirect:/food?date=2026-07-30");
        assertThat(foodEntryService.createdRequest).isSameAs(request);
        assertThat(request.getEatenAt()).isEqualTo(LocalDateTime.of(2026, 7, 30, 20, 0));
    }

    @Test
    void editRedirectsToUnchangedDateAndAcrossMonthAndYearBoundaries() {
        assertUpdateRedirect(LocalDateTime.of(2026, 7, 30, 9, 0), "redirect:/food?date=2026-07-30");
        assertUpdateRedirect(LocalDateTime.of(2026, 8, 1, 9, 0), "redirect:/food");
        assertUpdateRedirect(LocalDateTime.of(2027, 1, 1, 9, 0), "redirect:/food?date=2027-01-01");
    }

    @Test
    void createValidationFailurePreservesSubmittedEatenAt() {
        FoodEntryRequest request = new FoodEntryRequest();
        request.setEatenAt(LocalDateTime.of(2026, 7, 30, 20, 0));
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(request, "foodEntryRequest");
        bindingResult.rejectValue("amount", "invalid", "invalid amount");
        ConcurrentModel model = new ConcurrentModel();
        model.addAttribute("foodEntryRequest", request);

        String view = controller.createFoodEntry(
                request,
                bindingResult,
                "2026-07-31",
                model,
                new RedirectAttributesModelMap()
        );

        assertThat(view).isEqualTo("food");
        assertThat(((FoodEntryRequest) model.getAttribute("foodEntryRequest")).getEatenAt())
                .isEqualTo(LocalDateTime.of(2026, 7, 30, 20, 0));
    }

    private void assertUpdateRedirect(LocalDateTime eatenAt, String expectedRedirect) {
        Long id = (long) eatenAt.getDayOfYear();
        FoodEntryEditRequest request = new FoodEntryEditRequest();
        request.setEatenAt(eatenAt);
        foodEntryService.editResponse = response(id, eatenAt.minusDays(1));
        foodEntryService.updatedResponse = response(id, eatenAt);

        String redirect = controller.updateFoodEntry(
                id,
                request,
                new BeanPropertyBindingResult(request, "foodEntryEditRequest"),
                new ConcurrentModel(),
                new RedirectAttributesModelMap()
        );

        assertThat(redirect).isEqualTo(expectedRedirect);
        assertThat(foodEntryService.updatedId).isEqualTo(id);
        assertThat(foodEntryService.updatedRequest).isSameAs(request);
    }

    private FoodEntryResponse response(Long id, LocalDateTime eatenAt) {
        return new FoodEntryResponse(
                id, null, null, null, null, null, null, "Food", null, null,
                null, null, null, null, null, null, MealType.SNACK, eatenAt, null, null
        );
    }

    private static final class StubFoodEntryService extends FoodEntryService {
        private FoodEntryRequest newRequest;
        private FoodEntryRequest createdRequest;
        private FoodEntryResponse createdResponse;
        private FoodEntryResponse editResponse;
        private Long updatedId;
        private FoodEntryEditRequest updatedRequest;
        private FoodEntryResponse updatedResponse;

        private StubFoodEntryService() {
            super(null, null, null, CLOCK);
        }

        @Override
        public FoodEntryRequest newRequestForCurrentTime(Long savedFoodId) {
            return newRequest;
        }

        @Override
        public List<FoodEntryResponse> listEntriesForDate(LocalDate date) {
            return List.of();
        }

        @Override
        public Optional<String> validateCreate(FoodEntryRequest request) {
            return Optional.empty();
        }

        @Override
        public FoodEntryResponse create(FoodEntryRequest request) {
            createdRequest = request;
            return createdResponse;
        }

        @Override
        public FoodEntryResponse getForEdit(Long id) {
            return editResponse;
        }

        @Override
        public Optional<EditValidationError> validateUpdate(Long id, FoodEntryEditRequest request) {
            return Optional.empty();
        }

        @Override
        public FoodEntryResponse update(Long id, FoodEntryEditRequest request) {
            updatedId = id;
            updatedRequest = request;
            return updatedResponse;
        }
    }

    private static final class StubSavedFoodService extends SavedFoodService {
        private StubSavedFoodService() {
            super(null, null, CLOCK);
        }

        @Override
        public List<SavedFoodResponse> listActiveSavedFoods() {
            return List.of();
        }
    }
}

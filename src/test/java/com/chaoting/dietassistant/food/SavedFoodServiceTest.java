package com.chaoting.dietassistant.food;

import com.chaoting.dietassistant.profile.ProfileResponse;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SavedFoodServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-06-22T10:15:30Z"),
            ZoneOffset.UTC
    );

    @Test
    void createStoresSavedFoodForCurrentProfileAndTrimsText() {
        FakeSavedFoodRepository savedFoodRepository = new FakeSavedFoodRepository();
        SavedFoodService savedFoodService = service(savedFoodRepository);

        SavedFoodResponse response = savedFoodService.create(request(
                "  Greek yogurt  ",
                "  Chobani  ",
                "1.00",
                " cup ",
                "227.00",
                "100.00",
                "10.00",
                "5.00",
                "2.00",
                "0.00",
                "  Breakfast staple  "
        ));

        assertThat(savedFoodRepository.savedFood().getProfileId()).isEqualTo(7L);
        assertThat(response.name()).isEqualTo("Greek yogurt");
        assertThat(response.brand()).isEqualTo("Chobani");
        assertThat(response.referenceUnit()).isEqualTo("cup");
        assertThat(response.notes()).isEqualTo("Breakfast staple");
        assertThat(response.active()).isTrue();
        assertThat(response.createdAt()).isEqualTo(LocalDateTime.of(2026, 6, 22, 10, 15, 30));
        assertThat(response.updatedAt()).isEqualTo(LocalDateTime.of(2026, 6, 22, 10, 15, 30));
    }

    @Test
    void createConvertsBlankOptionalTextToNull() {
        SavedFoodService savedFoodService = service(new FakeSavedFoodRepository());

        SavedFoodResponse response = savedFoodService.create(request(
                "Water",
                "   ",
                "1.00",
                "glass",
                null,
                "0.00",
                "0.00",
                "0.00",
                "0.00",
                "0.00",
                "   "
        ));

        assertThat(response.brand()).isNull();
        assertThat(response.referenceWeightGrams()).isNull();
        assertThat(response.notes()).isNull();
    }

    @Test
    void listActiveSavedFoodsReturnsRepositoryOrder() {
        FakeSavedFoodRepository savedFoodRepository = new FakeSavedFoodRepository();
        savedFoodRepository.savedFoods = List.of(savedFood("Apple"), savedFood("Bread"));
        SavedFoodService savedFoodService = service(savedFoodRepository);

        List<SavedFoodResponse> responses = savedFoodService.listActiveSavedFoods();

        assertThat(responses).extracting(SavedFoodResponse::name).containsExactly("Apple", "Bread");
    }

    @Test
    void searchMatchesNameOrBrandAndCanIncludeInactiveFoods() {
        FakeSavedFoodRepository savedFoodRepository = new FakeSavedFoodRepository();
        SavedFood apple = savedFood("Apple");
        SavedFood yogurt = savedFood("Greek yogurt");
        yogurt.setBrand("Chobani");
        yogurt.setActive(false);
        savedFoodRepository.allSavedFoods = List.of(apple, yogurt);
        SavedFoodService savedFoodService = service(savedFoodRepository);

        assertThat(savedFoodService.searchSavedFoods("CHO", false)).isEmpty();
        assertThat(savedFoodService.searchSavedFoods("CHO", true))
                .extracting(SavedFoodResponse::name)
                .containsExactly("Greek yogurt");
        assertThat(savedFoodService.searchSavedFoods("app", true))
                .extracting(SavedFoodResponse::name)
                .containsExactly("Apple");
    }

    @Test
    void reactivateMarksSavedFoodActive() {
        FakeSavedFoodRepository savedFoodRepository = new FakeSavedFoodRepository();
        SavedFood existing = savedFood("Apple");
        existing.setActive(false);
        savedFoodRepository.savedFoodById = Optional.of(existing);
        SavedFoodService savedFoodService = service(savedFoodRepository);

        savedFoodService.reactivate(10L);

        assertThat(savedFoodRepository.savedFood().isActive()).isTrue();
        assertThat(savedFoodRepository.savedFood().getUpdatedAt())
                .isEqualTo(LocalDateTime.of(2026, 6, 22, 10, 15, 30));
    }

    @Test
    void updateChangesFieldsAndUpdatedAtOnly() {
        FakeSavedFoodRepository savedFoodRepository = new FakeSavedFoodRepository();
        SavedFood existing = savedFood("Old name");
        existing.setCreatedAt(LocalDateTime.of(2026, 1, 1, 8, 0));
        savedFoodRepository.savedFoodById = Optional.of(existing);
        SavedFoodService savedFoodService = service(savedFoodRepository);

        Optional<SavedFoodResponse> response = savedFoodService.update(10L, request(
                "New name",
                "Brand",
                "2.00",
                "serving",
                null,
                "200.00",
                "20.00",
                "10.00",
                "4.00",
                "2.00",
                null
        ));

        assertThat(response).isPresent();
        assertThat(response.orElseThrow().name()).isEqualTo("New name");
        assertThat(response.orElseThrow().createdAt()).isEqualTo(LocalDateTime.of(2026, 1, 1, 8, 0));
        assertThat(response.orElseThrow().updatedAt()).isEqualTo(LocalDateTime.of(2026, 6, 22, 10, 15, 30));
    }

    @Test
    void deactivateMarksSavedFoodInactive() {
        FakeSavedFoodRepository savedFoodRepository = new FakeSavedFoodRepository();
        SavedFood existing = savedFood("Apple");
        savedFoodRepository.savedFoodById = Optional.of(existing);
        SavedFoodService savedFoodService = service(savedFoodRepository);

        savedFoodService.deactivate(10L);

        assertThat(savedFoodRepository.savedFood().isActive()).isFalse();
        assertThat(savedFoodRepository.savedFood().getUpdatedAt()).isEqualTo(LocalDateTime.of(2026, 6, 22, 10, 15, 30));
    }

    @Test
    void createRequiresProfile() {
        SavedFoodService savedFoodService = new SavedFoodService(
                new FakeSavedFoodRepository().proxy(),
                Optional::empty,
                FIXED_CLOCK
        );

        assertThatThrownBy(() -> savedFoodService.create(request(
                "Apple",
                null,
                "1.00",
                "piece",
                null,
                "95.00",
                "0.50",
                "25.00",
                "0.30",
                "4.00",
                null
        ))).isInstanceOf(IllegalStateException.class)
                .hasMessage("Create the user profile before saving foods.");
    }

    private SavedFoodService service(FakeSavedFoodRepository savedFoodRepository) {
        return new SavedFoodService(
                savedFoodRepository.proxy(),
                () -> Optional.of(profile()),
                FIXED_CLOCK
        );
    }

    private SavedFoodRequest request(
            String name,
            String brand,
            String referenceAmount,
            String referenceUnit,
            String referenceWeightGrams,
            String calories,
            String proteinGrams,
            String carbohydrateGrams,
            String fatGrams,
            String fiberGrams,
            String notes
    ) {
        SavedFoodRequest request = new SavedFoodRequest();
        request.setName(name);
        request.setBrand(brand);
        request.setReferenceAmount(new BigDecimal(referenceAmount));
        request.setReferenceUnit(referenceUnit);
        request.setReferenceWeightGrams(referenceWeightGrams == null ? null : new BigDecimal(referenceWeightGrams));
        request.setCalories(new BigDecimal(calories));
        request.setProteinGrams(new BigDecimal(proteinGrams));
        request.setCarbohydrateGrams(new BigDecimal(carbohydrateGrams));
        request.setFatGrams(new BigDecimal(fatGrams));
        request.setFiberGrams(new BigDecimal(fiberGrams));
        request.setNotes(notes);
        return request;
    }

    private ProfileResponse profile() {
        return new ProfileResponse(
                7L,
                1988,
                new BigDecimal("175.50"),
                new BigDecimal("72.25"),
                null,
                3,
                LocalTime.of(18, 30),
                null
        );
    }

    private SavedFood savedFood(String name) {
        SavedFood savedFood = new SavedFood();
        savedFood.setProfileId(7L);
        savedFood.setName(name);
        savedFood.setReferenceAmount(new BigDecimal("1.00"));
        savedFood.setReferenceUnit("piece");
        savedFood.setCalories(new BigDecimal("95.00"));
        savedFood.setProteinGrams(new BigDecimal("0.50"));
        savedFood.setCarbohydrateGrams(new BigDecimal("25.00"));
        savedFood.setFatGrams(new BigDecimal("0.30"));
        savedFood.setFiberGrams(new BigDecimal("4.00"));
        savedFood.setActive(true);
        savedFood.setCreatedAt(LocalDateTime.of(2026, 6, 22, 8, 0));
        savedFood.setUpdatedAt(LocalDateTime.of(2026, 6, 22, 8, 0));
        return savedFood;
    }

    private static class FakeSavedFoodRepository {

        private List<SavedFood> savedFoods = List.of();
        private List<SavedFood> allSavedFoods = List.of();
        private Optional<SavedFood> savedFoodById = Optional.empty();
        private SavedFood savedFood;

        SavedFoodRepository proxy() {
            return (SavedFoodRepository) Proxy.newProxyInstance(
                    SavedFoodRepository.class.getClassLoader(),
                    new Class<?>[]{SavedFoodRepository.class},
                    (proxy, method, args) -> {
                        if (method.getName().equals("save")) {
                            savedFood = (SavedFood) args[0];
                            return savedFood;
                        }
                        if (method.getName().equals("findByProfileIdAndActiveTrueOrderByNameAscBrandAscIdAsc")) {
                            return savedFoods;
                        }
                        if (method.getName().equals("findByProfileIdOrderByNameAscBrandAscIdAsc")) {
                            return allSavedFoods;
                        }
                        if (method.getName().equals("findByIdAndProfileId")) {
                            return savedFoodById;
                        }
                        if (method.getName().equals("toString")) {
                            return "FakeSavedFoodRepository";
                        }
                        throw new UnsupportedOperationException(method.getName());
                    }
            );
        }

        SavedFood savedFood() {
            return savedFood;
        }
    }
}

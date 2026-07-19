package com.chaoting.dietassistant.food;

import com.chaoting.dietassistant.profile.CurrentProfileProvider;
import com.chaoting.dietassistant.profile.ProfileResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class SavedFoodService {

    private final SavedFoodRepository savedFoodRepository;
    private final CurrentProfileProvider currentProfileProvider;
    private final Clock clock;

    public SavedFoodService(
            SavedFoodRepository savedFoodRepository,
            CurrentProfileProvider currentProfileProvider,
            Clock clock
    ) {
        this.savedFoodRepository = savedFoodRepository;
        this.currentProfileProvider = currentProfileProvider;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<SavedFoodResponse> listActiveSavedFoods() {
        return currentProfileProvider.getProfile()
                .map(profile -> savedFoodRepository.findByProfileIdAndActiveTrueOrderByNameAscBrandAscIdAsc(profile.id()).stream()
                        .map(this::toResponse)
                        .toList())
                .orElseGet(List::of);
    }

    @Transactional(readOnly = true)
    public Optional<SavedFoodResponse> getSavedFood(Long id) {
        return currentProfileProvider.getProfile()
                .flatMap(profile -> savedFoodRepository.findByIdAndProfileId(id, profile.id()))
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Optional<SavedFoodResponse> getActiveSavedFood(Long id) {
        return currentProfileProvider.getProfile()
                .flatMap(profile -> savedFoodRepository.findByIdAndProfileIdAndActiveTrue(id, profile.id()))
                .map(this::toResponse);
    }

    @Transactional
    public SavedFoodResponse create(SavedFoodRequest request) {
        ProfileResponse profile = requireProfile();
        LocalDateTime now = LocalDateTime.now(clock);
        SavedFood savedFood = new SavedFood();
        savedFood.setProfileId(profile.id());
        applyRequest(savedFood, request);
        savedFood.setActive(true);
        savedFood.setCreatedAt(now);
        savedFood.setUpdatedAt(now);
        return toResponse(savedFoodRepository.save(savedFood));
    }

    @Transactional
    public Optional<SavedFoodResponse> update(Long id, SavedFoodRequest request) {
        ProfileResponse profile = requireProfile();
        return savedFoodRepository.findByIdAndProfileId(id, profile.id())
                .map(savedFood -> {
                    applyRequest(savedFood, request);
                    savedFood.setUpdatedAt(LocalDateTime.now(clock));
                    return toResponse(savedFoodRepository.save(savedFood));
                });
    }

    @Transactional
    public void deactivate(Long id) {
        ProfileResponse profile = requireProfile();
        savedFoodRepository.findByIdAndProfileId(id, profile.id())
                .ifPresent(savedFood -> {
                    savedFood.setActive(false);
                    savedFood.setUpdatedAt(LocalDateTime.now(clock));
                    savedFoodRepository.save(savedFood);
                });
    }

    SavedFoodRequest toRequest(SavedFoodResponse response) {
        SavedFoodRequest request = new SavedFoodRequest();
        request.setName(response.name());
        request.setBrand(response.brand());
        request.setReferenceAmount(response.referenceAmount());
        request.setReferenceUnit(response.referenceUnit());
        request.setReferenceWeightGrams(response.referenceWeightGrams());
        request.setCalories(response.calories());
        request.setProteinGrams(response.proteinGrams());
        request.setCarbohydrateGrams(response.carbohydrateGrams());
        request.setFatGrams(response.fatGrams());
        request.setFiberGrams(response.fiberGrams());
        request.setNotes(response.notes());
        return request;
    }

    private void applyRequest(SavedFood savedFood, SavedFoodRequest request) {
        savedFood.setName(request.getName().trim());
        savedFood.setBrand(blankToNull(request.getBrand()));
        savedFood.setReferenceAmount(request.getReferenceAmount());
        savedFood.setReferenceUnit(request.getReferenceUnit().trim());
        savedFood.setReferenceWeightGrams(request.getReferenceWeightGrams());
        savedFood.setCalories(request.getCalories());
        savedFood.setProteinGrams(request.getProteinGrams());
        savedFood.setCarbohydrateGrams(request.getCarbohydrateGrams());
        savedFood.setFatGrams(request.getFatGrams());
        savedFood.setFiberGrams(request.getFiberGrams());
        savedFood.setNotes(blankToNull(request.getNotes()));
    }

    private ProfileResponse requireProfile() {
        return currentProfileProvider.getProfile()
                .orElseThrow(() -> new IllegalStateException("Create the user profile before saving foods."));
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private SavedFoodResponse toResponse(SavedFood savedFood) {
        return new SavedFoodResponse(
                savedFood.getId(),
                savedFood.getName(),
                savedFood.getBrand(),
                savedFood.getReferenceAmount(),
                savedFood.getReferenceUnit(),
                savedFood.getReferenceWeightGrams(),
                savedFood.getCalories(),
                savedFood.getProteinGrams(),
                savedFood.getCarbohydrateGrams(),
                savedFood.getFatGrams(),
                savedFood.getFiberGrams(),
                savedFood.getNotes(),
                savedFood.isActive(),
                savedFood.getCreatedAt(),
                savedFood.getUpdatedAt()
        );
    }
}

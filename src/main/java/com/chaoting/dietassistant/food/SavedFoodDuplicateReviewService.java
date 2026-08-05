package com.chaoting.dietassistant.food;

import jakarta.validation.Validator;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SavedFoodDuplicateReviewService {

    private static final Duration LIFETIME = Duration.ofMinutes(30);
    private final SavedFoodService savedFoodService;
    private final PendingFoodImportService pendingFoodImportService;
    private final Validator validator;
    private final Clock clock;
    private final Map<String, ReviewState> reviews = new ConcurrentHashMap<>();

    public SavedFoodDuplicateReviewService(SavedFoodService savedFoodService,
                                           PendingFoodImportService pendingFoodImportService,
                                           Validator validator, Clock clock) {
        this.savedFoodService = savedFoodService;
        this.pendingFoodImportService = pendingFoodImportService;
        this.validator = validator;
        this.clock = clock;
    }

    public Optional<String> beginManual(SavedFoodRequest request) {
        return begin(Source.MANUAL, null, request);
    }

    public Optional<String> beginPhoto(String importId, SavedFoodRequest request) {
        return begin(Source.PHOTO, importId, request);
    }

    public Optional<ReviewView> find(String token) {
        ReviewState state = validState(token);
        if (state == null) return Optional.empty();
        return Optional.of(new ReviewView(token, state.source(), state.importId(), copy(state.request()),
                savedFoodService.findActiveDuplicates(state.request())));
    }

    public synchronized Outcome useExisting(String token, Long candidateId) {
        ReviewState state = validState(token);
        if (state == null) return Outcome.MISSING;
        validate(state.request());
        boolean candidateIsStillValid = savedFoodService.findActiveDuplicates(state.request()).stream()
                .anyMatch(candidate -> candidate.id().equals(candidateId));
        if (!candidateIsStillValid) return Outcome.CANDIDATE_UNAVAILABLE;
        if (state.source() == Source.PHOTO
                && !pendingFoodImportService.confirm(state.importId(), () -> requireActiveMatch(state.request(), candidateId))) {
            return Outcome.MISSING;
        }
        reviews.remove(token);
        return Outcome.USED_EXISTING;
    }

    public synchronized Outcome createAnyway(String token) {
        ReviewState state = validState(token);
        if (state == null) return Outcome.MISSING;
        validate(state.request());
        if (state.source() == Source.PHOTO) {
            if (!pendingFoodImportService.confirm(state.importId(), () -> savedFoodService.create(state.request()))) {
                return Outcome.MISSING;
            }
        } else {
            savedFoodService.create(state.request());
        }
        reviews.remove(token);
        return Outcome.CREATED;
    }

    private Optional<String> begin(Source source, String importId, SavedFoodRequest request) {
        reviews.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(clock.instant()));
        List<SavedFoodResponse> candidates = savedFoodService.findActiveDuplicates(request);
        if (candidates.isEmpty()) return Optional.empty();
        String token = UUID.randomUUID().toString();
        reviews.put(token, new ReviewState(source, importId, copy(request), clock.instant().plus(LIFETIME)));
        return Optional.of(token);
    }

    private ReviewState validState(String token) {
        if (token == null) return null;
        ReviewState state = reviews.get(token);
        return state == null || !state.expiresAt().isAfter(clock.instant()) ? null : state;
    }

    private void requireActiveMatch(SavedFoodRequest request, Long candidateId) {
        if (savedFoodService.findActiveDuplicates(request).stream().noneMatch(candidate -> candidate.id().equals(candidateId))) {
            throw new CandidateUnavailableException();
        }
    }

    private void validate(SavedFoodRequest request) {
        if (!validator.validate(request).isEmpty()) throw new IllegalStateException("Saved food details are no longer valid.");
    }

    static SavedFoodRequest copy(SavedFoodRequest source) {
        SavedFoodRequest copy = new SavedFoodRequest();
        copy.setName(source.getName()); copy.setBrand(source.getBrand());
        copy.setReferenceAmount(source.getReferenceAmount()); copy.setReferenceUnit(source.getReferenceUnit());
        copy.setReferenceWeightGrams(source.getReferenceWeightGrams()); copy.setCalories(source.getCalories());
        copy.setProteinGrams(source.getProteinGrams()); copy.setCarbohydrateGrams(source.getCarbohydrateGrams());
        copy.setFatGrams(source.getFatGrams()); copy.setFiberGrams(source.getFiberGrams()); copy.setNotes(source.getNotes());
        return copy;
    }

    public enum Source { MANUAL, PHOTO }
    public enum Outcome { USED_EXISTING, CREATED, CANDIDATE_UNAVAILABLE, MISSING }
    public record ReviewView(String token, Source source, String importId, SavedFoodRequest requested,
                             List<SavedFoodResponse> candidates) { }
    private record ReviewState(Source source, String importId, SavedFoodRequest request, Instant expiresAt) { }
    static final class CandidateUnavailableException extends RuntimeException { }
}

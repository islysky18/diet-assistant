package com.chaoting.dietassistant.food;

import java.time.LocalDateTime;

public record PendingFoodImportView(
        String importId,
        String status,
        LocalDateTime createdAt,
        LocalDateTime expiresAt,
        boolean hasFrontPhoto,
        String nutritionFactsPhotoFile,
        String frontPhotoFile,
        boolean resultAvailable,
        String resultError
) {
}

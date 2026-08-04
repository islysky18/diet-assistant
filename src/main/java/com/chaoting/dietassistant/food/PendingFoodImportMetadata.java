package com.chaoting.dietassistant.food;

import java.time.LocalDateTime;

record PendingFoodImportMetadata(
        String importId,
        String status,
        LocalDateTime createdAt,
        LocalDateTime expiresAt,
        PhotoFiles nutrition,
        PhotoFiles front,
        LocalDateTime recognitionStartedAt,
        LocalDateTime recognitionCompletedAt,
        String recognitionError
) {
    record PhotoFiles(String originalFormat, String originalFile, String preparedFile) { }

    PendingFoodImportMetadata withStatus(String newStatus) {
        return new PendingFoodImportMetadata(
                importId,
                newStatus,
                createdAt,
                expiresAt,
                nutrition,
                front,
                recognitionStartedAt,
                recognitionCompletedAt,
                recognitionError
        );
    }

    PendingFoodImportMetadata processing(LocalDateTime startedAt) {
        return new PendingFoodImportMetadata(importId, "processing", createdAt, expiresAt, nutrition, front,
                startedAt, null, null);
    }

    PendingFoodImportMetadata completed(String newStatus, LocalDateTime completedAt, String error) {
        return new PendingFoodImportMetadata(importId, newStatus, createdAt, expiresAt, nutrition, front,
                recognitionStartedAt, completedAt, error);
    }

    PendingFoodImportMetadata retry() {
        return new PendingFoodImportMetadata(importId, "pending", createdAt, expiresAt, nutrition, front,
                null, null, null);
    }
}

package com.chaoting.dietassistant.food;

import java.nio.file.Path;
import java.util.Optional;

record FoodPhotoRecognitionRequest(String importId, Path nutritionPhoto, Optional<Path> frontPhoto) {

    FoodPhotoRecognitionRequest {
        if (importId == null || importId.isBlank()) throw new IllegalArgumentException("importId is required.");
        nutritionPhoto = nutritionPhoto.toAbsolutePath().normalize();
        frontPhoto = frontPhoto.map(path -> path.toAbsolutePath().normalize());
    }
}

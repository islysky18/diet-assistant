package com.chaoting.dietassistant.food;

interface FoodPhotoRecognizer {

    PendingFoodImportResult recognize(FoodPhotoRecognitionRequest request) throws FoodPhotoRecognitionException;
}

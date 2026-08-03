package com.chaoting.dietassistant.food;

import org.springframework.web.multipart.MultipartFile;

public class SavedFoodPhotoUploadRequest {

    private MultipartFile frontPhoto;
    private MultipartFile nutritionFactsPhoto;

    public MultipartFile getFrontPhoto() {
        return frontPhoto;
    }

    public void setFrontPhoto(MultipartFile frontPhoto) {
        this.frontPhoto = frontPhoto;
    }

    public MultipartFile getNutritionFactsPhoto() {
        return nutritionFactsPhoto;
    }

    public void setNutritionFactsPhoto(MultipartFile nutritionFactsPhoto) {
        this.nutritionFactsPhoto = nutritionFactsPhoto;
    }
}

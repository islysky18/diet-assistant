package com.chaoting.dietassistant.food;

enum ProductPhotoFormat {
    JPEG("jpg", "image/jpeg"),
    PNG("png", "image/png"),
    HEIC("heic", "image/heic"),
    HEIF("heif", "image/heif");

    final String extension;
    final String mimeType;

    ProductPhotoFormat(String extension, String mimeType) {
        this.extension = extension;
        this.mimeType = mimeType;
    }
}

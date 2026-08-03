package com.chaoting.dietassistant.food;

class FoodPhotoRecognitionException extends Exception {

    private final Reason reason;

    FoodPhotoRecognitionException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    FoodPhotoRecognitionException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    Reason reason() {
        return reason;
    }

    enum Reason {
        UNAVAILABLE,
        TIMEOUT,
        EXECUTION_FAILED,
        INVALID_RESULT
    }
}

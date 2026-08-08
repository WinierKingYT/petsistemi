package com.petsistemi.api.order;

public record PetOrderResult(boolean success, String message) {
    public static PetOrderResult success(String message) { return new PetOrderResult(true, message); }
    public static PetOrderResult failure(String message) { return new PetOrderResult(false, message); }
}

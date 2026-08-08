package com.petsistemi.api.item;

public record PetItemActionResult(boolean success, String message) {
    public static PetItemActionResult success(String message) { return new PetItemActionResult(true, message); }
    public static PetItemActionResult failure(String message) { return new PetItemActionResult(false, message); }
}

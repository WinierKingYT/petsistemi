package com.petsistemi.api.mount;

public record PetMountResult(PetMountStatus status, String message) {
    public boolean success() {
        return status == PetMountStatus.MOUNTED || status == PetMountStatus.DISMOUNTED;
    }
}

package com.petsistemi.domain;

import java.util.UUID;

public record PetInstance(
        UUID petId,
        UUID ownerId,
        String definitionId,
        String customName,
        int level,
        long experience,
        PetStorageState storageState,
        long createdAt,
        long updatedAt
) {
    public PetInstance withLevelAndExperience(int newLevel, long newExperience) {
        return new PetInstance(petId, ownerId, definitionId, customName, newLevel, newExperience, storageState, createdAt, System.currentTimeMillis());
    }

    public PetInstance withCustomName(String newCustomName) {
        return new PetInstance(petId, ownerId, definitionId, newCustomName, level, experience, storageState, createdAt, System.currentTimeMillis());
    }

    public PetInstance withStorageState(PetStorageState newStorageState) {
        return new PetInstance(petId, ownerId, definitionId, customName, level, experience, newStorageState, createdAt, System.currentTimeMillis());
    }
}

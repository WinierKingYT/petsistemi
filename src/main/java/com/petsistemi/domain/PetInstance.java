package com.petsistemi.domain;

import java.util.UUID;

public record PetInstance(
        UUID petId,
        UUID ownerId,
        String definitionId,
        String customName,
        int level,
        long experience,
        PetAvailabilityState availabilityState,
        long createdAt,
        long updatedAt
) {
    public PetInstance withLevelAndExperience(int newLevel, long newExperience) {
        return new PetInstance(petId, ownerId, definitionId, customName, newLevel, newExperience, availabilityState, createdAt, System.currentTimeMillis());
    }

    public PetInstance withCustomName(String newCustomName) {
        return new PetInstance(petId, ownerId, definitionId, newCustomName, level, experience, availabilityState, createdAt, System.currentTimeMillis());
    }

    public PetInstance withAvailabilityState(PetAvailabilityState newAvailabilityState) {
        return new PetInstance(petId, ownerId, definitionId, customName, level, experience, newAvailabilityState, createdAt, System.currentTimeMillis());
    }
}

package com.petsistemi.domain;

import java.util.UUID;

public record PetSelection(
        UUID ownerId,
        UUID petId,
        long selectedAt,
        PetFollowMode followMode
) {
    public PetSelection(UUID ownerId, UUID petId, long selectedAt) {
        this(ownerId, petId, selectedAt, PetFollowMode.FOLLOW);
    }

    public PetSelection withFollowMode(PetFollowMode newMode) {
        return new PetSelection(ownerId, petId, selectedAt, newMode != null ? newMode : PetFollowMode.FOLLOW);
    }
}

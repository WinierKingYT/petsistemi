package com.petsistemi.api;

import com.petsistemi.domain.PetStorageState;
import java.util.UUID;

public record PetSnapshot(
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
}

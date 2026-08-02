package com.petsistemi.api;

import com.petsistemi.domain.PetAvailabilityState;
import java.util.UUID;

public record PetSnapshot(
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
}

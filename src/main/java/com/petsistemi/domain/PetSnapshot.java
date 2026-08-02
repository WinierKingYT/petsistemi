package com.petsistemi.domain;

import java.util.UUID;

public record PetSnapshot(
        UUID petId,
        UUID ownerId,
        String definitionId,
        String customName,
        int level,
        long experience,
        PetAvailabilityState availability,
        boolean selected,
        boolean spawned
) {}

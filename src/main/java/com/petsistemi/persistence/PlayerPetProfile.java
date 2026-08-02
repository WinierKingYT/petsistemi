package com.petsistemi.persistence;

import com.petsistemi.api.PetSnapshot;

import java.util.Map;
import java.util.UUID;

public record PlayerPetProfile(
        UUID ownerId,
        Map<UUID, PetSnapshot> pets,
        UUID selectedPetId,
        long loadedAt,
        long version
) {}

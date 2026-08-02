package com.petsistemi.domain;

import java.util.UUID;

public record PetSelection(
        UUID ownerId,
        UUID petId,
        long selectedAt
) {}

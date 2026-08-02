package com.petsistemi.persistence;

import com.petsistemi.domain.PetSelection;

import java.util.Optional;
import java.util.UUID;

public interface PetSelectionRepository {

    Optional<PetSelection> findByOwner(UUID ownerId);

    void select(UUID ownerId, UUID petId);

    void clear(UUID ownerId);

    void switchSelection(UUID ownerId, UUID previousPetId, UUID newPetId);
}

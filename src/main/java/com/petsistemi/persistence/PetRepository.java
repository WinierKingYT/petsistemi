package com.petsistemi.persistence;

import com.petsistemi.domain.PetInstance;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PetRepository {

    Optional<PetInstance> findById(UUID petId);

    List<PetInstance> findByOwner(UUID ownerId);

    Optional<PetInstance> findActiveByOwner(UUID ownerId);

    void insert(PetInstance pet);

    void update(PetInstance pet);

    void delete(UUID petId);

    void setActivePet(UUID ownerId, UUID petId);

    void clearActivePet(UUID ownerId);

    void switchActivePet(UUID ownerId, UUID previousPetId, UUID newPetId);

    void clearActivePetAndSetAvailable(UUID ownerId, UUID petId);

    void restoreActivePet(UUID ownerId, UUID previousPetId, UUID failedPetId);

    default void disablePetTransactional(UUID ownerId, PetInstance updatedPet) {
        if (ownerId != null) clearActivePet(ownerId);
        update(updatedPet);
    }

    default void removePetTransactional(UUID ownerId, UUID petId) {
        if (ownerId != null) clearActivePet(ownerId);
        delete(petId);
    }
}

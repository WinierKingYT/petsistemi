package com.petsistemi.persistence;

import com.petsistemi.domain.PetInstance;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PetRepository {

    Optional<PetInstance> findById(UUID petId);

    List<PetInstance> findByOwner(UUID ownerId);

    /**
     * Finds the pet currently active (selected+spawned) for the given owner.
     * @deprecated Use {@link PetSelectionRepository#findByOwner(UUID)} for selection state;
     *             use {@link com.petsistemi.runtime.ActivePetRegistry} for spawned-entity state.
     */
    @Deprecated
    Optional<PetInstance> findActiveByOwner(UUID ownerId);

    void insert(PetInstance pet);

    void update(PetInstance pet);

    void delete(UUID petId);

    /**
     * @deprecated Selection state is owned by {@link PetSelectionRepository}.
     *             Use {@link PetSelectionRepository#select(UUID, UUID)} instead.
     */
    @Deprecated
    void setActivePet(UUID ownerId, UUID petId);

    /**
     * @deprecated Selection state is owned by {@link PetSelectionRepository}.
     *             Use {@link PetSelectionRepository#clear(UUID)} instead.
     */
    @Deprecated
    void clearActivePet(UUID ownerId);

    /**
     * @deprecated Selection state is owned by {@link PetSelectionRepository}.
     *             Use {@link PetSelectionRepository#switchSelection(UUID, UUID, UUID)} instead.
     */
    @Deprecated
    void switchActivePet(UUID ownerId, UUID previousPetId, UUID newPetId);

    /**
     * @deprecated Selection state is owned by {@link PetSelectionRepository}.
     */
    @Deprecated
    void clearActivePetAndSetAvailable(UUID ownerId, UUID petId);

    /**
     * @deprecated Selection state is owned by {@link PetSelectionRepository}.
     */
    @Deprecated
    void restoreActivePet(UUID ownerId, UUID previousPetId, UUID failedPetId);

    default void disablePetTransactional(UUID clearSelectionOwnerId, PetInstance updatedPet) {
        // Default implementation delegates to deprecated methods for backward compatibility.
        // Override in production with a real JDBC transaction.
        if (clearSelectionOwnerId != null) clearActivePet(clearSelectionOwnerId);
        update(updatedPet);
    }

    default void removePetTransactional(UUID clearSelectionOwnerId, UUID petId) {
        // Default implementation delegates to deprecated methods for backward compatibility.
        // Override in production with a real JDBC transaction.
        if (clearSelectionOwnerId != null) clearActivePet(clearSelectionOwnerId);
        delete(petId);
    }
}


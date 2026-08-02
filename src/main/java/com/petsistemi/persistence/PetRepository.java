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
}

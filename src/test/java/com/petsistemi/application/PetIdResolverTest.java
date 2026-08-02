package com.petsistemi.application;

import com.petsistemi.domain.PetAvailabilityState;
import com.petsistemi.domain.PetInstance;
import com.petsistemi.persistence.PetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PetIdResolverTest {

    private PetRepository mockRepository;
    private UUID ownerId;
    private UUID pet1Id;
    private UUID pet2Id;

    @BeforeEach
    void setUp() {
        ownerId = UUID.randomUUID();
        pet1Id = UUID.fromString("11111111-1111-1111-1111-111111111111");
        pet2Id = UUID.fromString("22222222-2222-2222-2222-222222222222");

        List<PetInstance> pets = new ArrayList<>();
        pets.add(new PetInstance(pet1Id, ownerId, "wolf", "Wolfy", 1, 0, PetAvailabilityState.AVAILABLE, 100, 100));
        pets.add(new PetInstance(pet2Id, ownerId, "cat", "Kitty", 1, 0, PetAvailabilityState.AVAILABLE, 100, 100));

        mockRepository = new PetRepository() {
            @Override public Optional<PetInstance> findById(UUID petId) {
                return pets.stream().filter(p -> p.petId().equals(petId)).findFirst();
            }
            @Override public List<PetInstance> findByOwner(UUID owner) {
                return pets.stream().filter(p -> p.ownerId().equals(owner)).toList();
            }
            @Override public Optional<PetInstance> findActiveByOwner(UUID ownerId) { return Optional.empty(); }
            @Override public void insert(PetInstance pet) {}
            @Override public void update(PetInstance pet) {}
            @Override public void delete(UUID petId) {}
            @Override public void setActivePet(UUID ownerId, UUID petId) {}
            @Override public void clearActivePet(UUID ownerId) {}
            @Override public void switchActivePet(UUID ownerId, UUID previousPetId, UUID newPetId) {}
            @Override public void clearActivePetAndSetAvailable(UUID ownerId, UUID petId) {}
            @Override public void restoreActivePet(UUID ownerId, UUID previousPetId, UUID failedPetId) {}
        };
    }

    @Test
    void testResolveFullUuid() {
        PetIdResolver.ResolutionResult result = PetIdResolver.resolve(pet1Id.toString(), ownerId, mockRepository);
        assertInstanceOf(PetIdResolver.ResolutionResult.Found.class, result);
        assertEquals(pet1Id, ((PetIdResolver.ResolutionResult.Found) result).petId());
    }

    @Test
    void testResolveShortUuidPrefix() {
        PetIdResolver.ResolutionResult result = PetIdResolver.resolve("1111", ownerId, mockRepository);
        assertInstanceOf(PetIdResolver.ResolutionResult.Found.class, result);
        assertEquals(pet1Id, ((PetIdResolver.ResolutionResult.Found) result).petId());
    }

    @Test
    void testResolveNotFound() {
        PetIdResolver.ResolutionResult result = PetIdResolver.resolve("9999", ownerId, mockRepository);
        assertInstanceOf(PetIdResolver.ResolutionResult.NotFound.class, result);
    }
}

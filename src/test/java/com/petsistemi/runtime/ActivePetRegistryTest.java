package com.petsistemi.runtime;

import com.petsistemi.domain.PetRuntimeState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ActivePetRegistryTest {

    private ActivePetRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ActivePetRegistry();
    }

    @Test
    void testRegisterAndRetrieve() {
        UUID petId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID entityId = UUID.randomUUID();

        ActivePet activePet = new ActivePet(petId, ownerId, entityId, null, PetRuntimeState.ACTIVE);
        registry.register(activePet);

        assertTrue(registry.getByOwner(ownerId).isPresent());
        assertEquals(petId, registry.getByOwner(ownerId).get().getPetId());

        assertTrue(registry.getByEntity(entityId).isPresent());
        assertEquals(ownerId, registry.getByEntity(entityId).get().getOwnerId());
    }

    @Test
    void testUnregister() {
        UUID petId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID entityId = UUID.randomUUID();

        ActivePet activePet = new ActivePet(petId, ownerId, entityId, null, PetRuntimeState.ACTIVE);
        registry.register(activePet);
        registry.unregister(ownerId);

        assertTrue(registry.getByOwner(ownerId).isEmpty());
        assertTrue(registry.getByEntity(entityId).isEmpty());
    }

    @Test
    void testClearDoesNotThrowUnsupportedOperationException() {
        UUID petId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID entityId = UUID.randomUUID();

        ActivePet activePet = new ActivePet(petId, ownerId, entityId, null, PetRuntimeState.ACTIVE);
        registry.register(activePet);

        assertDoesNotThrow(() -> registry.clear());
        assertTrue(registry.getAllActive().isEmpty());
    }
}

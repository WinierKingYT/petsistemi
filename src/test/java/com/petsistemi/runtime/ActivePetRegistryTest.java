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

    @Test
    void testGetByAnyEntityResolvesChildren() {
        UUID petId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID primaryId = UUID.randomUUID();
        UUID childId = UUID.randomUUID();

        ActivePet activePet = new ActivePet(petId, ownerId, primaryId, null, PetRuntimeState.ACTIVE);
        org.bukkit.entity.Entity child = org.mockito.Mockito.mock(org.bukkit.entity.Entity.class);
        org.mockito.Mockito.when(child.getUniqueId()).thenReturn(childId);
        activePet.addChild(child);
        registry.register(activePet);

        assertTrue(registry.getByAnyEntity(primaryId).isPresent(), "primary entity must resolve");
        assertTrue(registry.getByAnyEntity(childId).isPresent(), "child entity must resolve");
        assertEquals(petId, registry.getByAnyEntity(childId).get().getPetId());

        UUID unknown = UUID.randomUUID();
        assertTrue(registry.getByAnyEntity(unknown).isEmpty(), "unknown entity must not resolve");
    }

    @Test
    void testGetByAnyEntityAfterUnregisterIsEmpty() {
        UUID ownerId = UUID.randomUUID();
        UUID primaryId = UUID.randomUUID();
        UUID childId = UUID.randomUUID();

        ActivePet activePet = new ActivePet(UUID.randomUUID(), ownerId, primaryId, null, PetRuntimeState.ACTIVE);
        org.bukkit.entity.Entity child = org.mockito.Mockito.mock(org.bukkit.entity.Entity.class);
        org.mockito.Mockito.when(child.getUniqueId()).thenReturn(childId);
        activePet.addChild(child);
        registry.register(activePet);
        registry.unregister(ownerId);

        assertTrue(registry.getByAnyEntity(primaryId).isEmpty());
        assertTrue(registry.getByAnyEntity(childId).isEmpty());
    }

    @Test
    void testPetInstanceLevelSync() {
        UUID petId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        com.petsistemi.domain.PetInstance instance = new com.petsistemi.domain.PetInstance(
                petId, ownerId, "wolf", "Kurt", 7, 250, com.petsistemi.domain.PetAvailabilityState.AVAILABLE, 0, 0);

        ActivePet activePet = new ActivePet(petId, ownerId, UUID.randomUUID(), null, PetRuntimeState.ACTIVE);
        activePet.setPetInstance(instance);

        assertEquals(7, activePet.getLevel(), "setPetInstance must sync the level");
        assertEquals(instance, activePet.getPetInstance());

        activePet.setLevel(12);
        assertEquals(12, activePet.getLevel());
        assertEquals(12, activePet.getPetInstance().level(), "setLevel must refresh the stored instance");
    }
}

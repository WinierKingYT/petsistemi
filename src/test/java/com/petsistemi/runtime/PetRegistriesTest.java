package com.petsistemi.runtime;

import com.petsistemi.domain.PetDefinition;
import com.petsistemi.domain.PetInstance;
import com.petsistemi.domain.PetMovementType;
import com.petsistemi.domain.RuntimeRepresentationType;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PetRegistriesTest {

    private static PetRepresentationController dummyRep() {
        return new PetRepresentationController() {
            @Override public Entity spawn(PetInstance pet, PetDefinition definition, Player owner) { return null; }
            @Override public void updateVisual(Entity primaryEntity, PetInstance pet, PetDefinition definition) {}
            @Override public void remove(Entity primaryEntity) {}
            @Override public boolean isValid(Entity primaryEntity) { return false; }
        };
    }

    private static PetMovementController dummyMovement() {
        return new PetMovementController() {
            @Override public void initialize(ActivePet activePet, Entity entity, Player owner) {}
            @Override public void tick(ActivePet activePet, Entity entity, Player owner) {}
            @Override public void remove(ActivePet activePet, Entity entity) {}
        };
    }

    @Test
    void representationRegistryStoresAndReturnsControllers() {
        PetRepresentationRegistry registry = new PetRepresentationRegistry();
        PetRepresentationController rep = dummyRep();

        assertNull(registry.get(RuntimeRepresentationType.ENTITY));
        assertFalse(registry.supported().contains(RuntimeRepresentationType.ENTITY));

        registry.register(RuntimeRepresentationType.ITEM_DISPLAY, rep);

        assertSame(rep, registry.get(RuntimeRepresentationType.ITEM_DISPLAY));
        assertTrue(registry.supported().contains(RuntimeRepresentationType.ITEM_DISPLAY));
    }

    @Test
    void representationRegistryIgnoresNullArguments() {
        PetRepresentationRegistry registry = new PetRepresentationRegistry();
        registry.register(null, dummyRep());
        registry.register(RuntimeRepresentationType.ENTITY, null);
        assertTrue(registry.supported().isEmpty());
    }

    @Test
    void movementRegistryStoresAndReturnsControllers() {
        PetMovementRegistry registry = new PetMovementRegistry();
        PetMovementController movement = dummyMovement();

        assertNull(registry.get(PetMovementType.ORBIT));

        registry.register(PetMovementType.ORBIT, movement);

        assertSame(movement, registry.get(PetMovementType.ORBIT));
        assertTrue(registry.supported().contains(PetMovementType.ORBIT));
    }

    @Test
    void movementRegistryGetWithNullTypeReturnsGroundFollow() {
        PetMovementRegistry registry = new PetMovementRegistry();
        PetMovementController ground = dummyMovement();
        registry.register(PetMovementType.GROUND_FOLLOW, ground);
        assertSame(ground, registry.get(null));
    }
}

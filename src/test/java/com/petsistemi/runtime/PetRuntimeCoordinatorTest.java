package com.petsistemi.runtime;

import com.petsistemi.definition.PetDefinitionRegistry;
import com.petsistemi.domain.PetAvailabilityState;
import com.petsistemi.domain.PetDefinition;
import com.petsistemi.domain.PetInstance;
import com.petsistemi.domain.PetRuntimeState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PetRuntimeCoordinatorTest {

    private ActivePetRegistry activeRegistry;
    private PetRuntimeCoordinator coordinator;

    @BeforeEach
    void setUp() {
        activeRegistry = new ActivePetRegistry();
    }

    @Test
    void testSpawnUncommittedSpawnsEntityWithoutRegisteringInActiveRegistry() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID petId = UUID.randomUUID();

        PetDefinition def = new PetDefinition("wolf", "Wolf", Collections.emptyList(), "WOLF", false, false, true, false, true, true, 100, true, Collections.emptyList());
        PetDefinitionRegistry defRegistry = new PetDefinitionRegistry() {
            @Override public Optional<PetDefinition> find(String id) { return Optional.of(def); }
            @Override public java.util.Collection<PetDefinition> getAll() { return Collections.emptyList(); }
            @Override public void reload() {}
        };

        PetEntityController entityController = new PetEntityController() {
            @Override public Entity spawn(PetInstance instance, PetDefinition def, Player owner) {
                LivingEntity living = org.mockito.Mockito.mock(LivingEntity.class);
                org.mockito.Mockito.when(living.getUniqueId()).thenReturn(UUID.randomUUID());
                org.mockito.Mockito.when(living.isValid()).thenReturn(true);
                return living;
            }
            @Override public void remove(Entity entity) {}
            @Override public void updateName(Entity entity, PetInstance instance, PetDefinition def) {}
            @Override public boolean isValid(Entity entity) { return true; }
        };

        PetBehaviorController behaviorController = new PetBehaviorController() {
            @Override public void initialize(ActivePet activePet, LivingEntity entity, Player owner) {}
            @Override public void tick(ActivePet activePet, LivingEntity entity, Player owner) {}
            @Override public void remove(ActivePet activePet, LivingEntity entity) {}
        };

        Player mockPlayer = org.mockito.Mockito.mock(Player.class);
        org.mockito.Mockito.when(mockPlayer.getUniqueId()).thenReturn(ownerId);

        coordinator = new PetRuntimeCoordinator(null, defRegistry, activeRegistry, entityController, behaviorController);

        PetInstance instance = new PetInstance(petId, ownerId, "wolf", "Kurt", 1, 0, PetAvailabilityState.AVAILABLE, 0, 0);
        Entity spawned = coordinator.spawnRuntimeUncommitted(mockPlayer, instance, def);

        assertNotNull(spawned);
        assertTrue(activeRegistry.getByOwner(ownerId).isEmpty(), "Uncommitted spawn must not register in ActivePetRegistry yet");

        ActivePet activePet = new ActivePet(petId, ownerId, "wolf", 1, spawned.getUniqueId(), spawned, PetRuntimeState.ACTIVE);
        coordinator.commitRuntimeSpawn(activePet);

        assertTrue(activeRegistry.getByOwner(ownerId).isPresent(), "Committed spawn must register in ActivePetRegistry");
    }

    @Test
    void testDespawnRuntimeRemovesFromActiveRegistry() {
        UUID ownerId = UUID.randomUUID();
        UUID petId = UUID.randomUUID();

        ActivePet active = new ActivePet(petId, ownerId, "wolf", 1, UUID.randomUUID(), null, PetRuntimeState.ACTIVE);
        activeRegistry.register(active);

        coordinator = new PetRuntimeCoordinator(null, null, activeRegistry, null, null);
        coordinator.despawnRuntime(ownerId);

        assertTrue(activeRegistry.getByOwner(ownerId).isEmpty());
    }
}

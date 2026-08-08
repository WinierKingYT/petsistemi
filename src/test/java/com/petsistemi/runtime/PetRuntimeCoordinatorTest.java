package com.petsistemi.runtime;

import com.petsistemi.definition.PetDefinitionRegistry;
import com.petsistemi.domain.PetAvailabilityState;
import com.petsistemi.domain.PetDefinition;
import com.petsistemi.domain.PetInstance;
import com.petsistemi.domain.PetRuntimeState;
import com.petsistemi.domain.PetRepresentationDefinition;
import com.petsistemi.domain.PetVector3;
import com.petsistemi.domain.RuntimeRepresentationType;
import com.petsistemi.domain.visual.PetVisualTransform;
import com.petsistemi.runtime.visual.PetRenderBackend;
import com.petsistemi.runtime.visual.PetVisualComponent;
import com.petsistemi.runtime.visual.PetVisualHandle;
import org.bukkit.NamespacedKey;
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

    @Test
    void missingExternalProviderRejectsSummonInsteadOfSilentlySpawningVanillaEntity() {
        UUID ownerId = UUID.randomUUID();
        UUID petId = UUID.randomUUID();
        NamespacedKey providerKey = new NamespacedKey("modelengine", "model");
        PetRepresentationDefinition representation = new PetRepresentationDefinition(
                providerKey, "phoenix", "ARMOR_STAND", false, false, true, true, false,
                null, null, PetVector3.ONE, null, 0, 0, 0, 0, null);
        PetDefinition definition = PetDefinition.builder("phoenix", "Phoenix")
                .representation(representation).build();
        PetInstance instance = new PetInstance(petId, ownerId, "phoenix", "Phoenix", 1, 0,
                PetAvailabilityState.AVAILABLE, 0, 0);
        Player owner = org.mockito.Mockito.mock(Player.class);
        org.mockito.Mockito.when(owner.getUniqueId()).thenReturn(ownerId);
        PetEntityController legacy = org.mockito.Mockito.mock(PetEntityController.class);
        coordinator = new PetRuntimeCoordinator(null, null, activeRegistry, legacy, null,
                new PetRepresentationRegistry(), new PetMovementRegistry());

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> coordinator.spawnRuntimeUncommittedHandle(owner, instance, definition));

        assertTrue(error.getMessage().contains("modelengine:model"));
        org.mockito.Mockito.verifyNoInteractions(legacy);
    }

    @Test
    void modularSpawnAdoptsGraphHandleAndNamedChildrenWithoutLegacyRespawn() throws Exception {
        UUID ownerId = UUID.randomUUID();
        Entity root = org.mockito.Mockito.mock(Entity.class);
        Entity crown = org.mockito.Mockito.mock(Entity.class);
        org.mockito.Mockito.when(root.getUniqueId()).thenReturn(UUID.randomUUID());
        org.mockito.Mockito.when(crown.getUniqueId()).thenReturn(UUID.randomUUID());
        org.mockito.Mockito.when(root.isValid()).thenReturn(true);
        org.mockito.Mockito.when(crown.isValid()).thenReturn(true);

        NamespacedKey key = com.petsistemi.domain.RuntimeKeyResolver
                .representationKey(RuntimeRepresentationType.ITEM_DISPLAY);
        PetVisualHandle visual = PetVisualHandle.builder("body", PetRenderBackend.SERVER)
                .component(new PetVisualComponent("body", null, key, PetVisualTransform.IDENTITY, root))
                .component(new PetVisualComponent("crown", "body", key, PetVisualTransform.IDENTITY, crown))
                .build();
        PetRepresentationController controller = new PetRepresentationController() {
            @Override public Entity spawn(PetInstance pet, PetDefinition definition, Player owner) {
                throw new AssertionError("Graph-aware spawn legacy spawn yoluna düşmemeli");
            }
            @Override public PetVisualHandle spawnVisual(PetInstance pet, PetDefinition definition, Player owner) {
                return visual;
            }
            @Override public void updateVisual(Entity primaryEntity, PetInstance pet, PetDefinition definition) {}
            @Override public void remove(Entity primaryEntity) {}
            @Override public boolean isValid(Entity primaryEntity) { return true; }
        };
        PetRepresentationRegistry representations = new PetRepresentationRegistry();
        representations.register(RuntimeRepresentationType.ITEM_DISPLAY, controller);
        coordinator = new PetRuntimeCoordinator(null, null, activeRegistry, null, null,
                representations, new PetMovementRegistry());

        Player owner = org.mockito.Mockito.mock(Player.class);
        org.mockito.Mockito.when(owner.getUniqueId()).thenReturn(ownerId);
        PetDefinition definition = PetDefinition.builder("drone", "Drone")
                .representation(PetRepresentationDefinition.display(
                        RuntimeRepresentationType.ITEM_DISPLAY, "PAPER", 1001, PetVector3.ONE))
                .build();
        PetInstance instance = new PetInstance(UUID.randomUUID(), ownerId, "drone", "Drone", 1, 0,
                PetAvailabilityState.AVAILABLE, 0, 0);

        ActivePet active = coordinator.spawnRuntimeUncommittedHandle(owner, instance, definition);

        assertSame(visual, active.getVisualHandle());
        assertSame(root, active.getSpawnedEntity());
        assertEquals(java.util.List.of(crown), active.getChildren());
        assertEquals(java.util.Set.of("body", "crown"), active.getVisualHandle().components().stream()
                .map(PetVisualComponent::id).collect(java.util.stream.Collectors.toSet()));
    }

    @Test
    void mountedPetSkipsNormalMovementController() {
        UUID ownerId = UUID.randomUUID();
        org.bukkit.World world = org.mockito.Mockito.mock(org.bukkit.World.class);
        org.mockito.Mockito.when(world.isChunkLoaded(org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt())).thenReturn(true);
        org.bukkit.Location location = new org.bukkit.Location(world, 0, 64, 0);
        Player owner = org.mockito.Mockito.mock(Player.class);
        org.mockito.Mockito.when(owner.isOnline()).thenReturn(true);
        org.mockito.Mockito.when(owner.getLocation()).thenReturn(location);
        org.mockito.Mockito.when(owner.getWorld()).thenReturn(world);
        Entity entity = org.mockito.Mockito.mock(Entity.class);
        org.mockito.Mockito.when(entity.isValid()).thenReturn(true);
        org.mockito.Mockito.when(entity.getLocation()).thenReturn(location);
        org.mockito.Mockito.when(entity.getWorld()).thenReturn(world);
        ActivePet active = new ActivePet(UUID.randomUUID(), ownerId, "wolf", 1,
                UUID.randomUUID(), entity, PetRuntimeState.ACTIVE);
        active.setUpdateIntervalTicks(0);

        PetMovementController movement = org.mockito.Mockito.mock(PetMovementController.class);
        PetMovementRegistry movements = new PetMovementRegistry();
        movements.register(com.petsistemi.domain.PetMovementType.GROUND_FOLLOW, movement);
        coordinator = new PetRuntimeCoordinator(null, null, activeRegistry, null, null, null, movements);
        com.petsistemi.runtime.mount.PetMountController mounts =
                org.mockito.Mockito.mock(com.petsistemi.runtime.mount.PetMountController.class);
        org.mockito.Mockito.when(mounts.tick(active, owner)).thenReturn(true);
        coordinator.setMountController(mounts);

        coordinator.tickEach(java.util.List.of(active), ignored -> owner);

        org.mockito.Mockito.verify(mounts).tick(active, owner);
        org.mockito.Mockito.verify(movement, org.mockito.Mockito.never()).tick(active, entity, owner);
    }
}

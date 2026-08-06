package com.petsistemi.runtime;

import com.petsistemi.config.PluginConfiguration;
import com.petsistemi.config.RuntimeConfigurationSnapshot;
import com.petsistemi.definition.PetDefinitionRegistry;
import com.petsistemi.domain.PetAvailabilityState;
import com.petsistemi.domain.PetDefinition;
import com.petsistemi.domain.PetFollowMode;
import com.petsistemi.domain.PetInstance;
import com.petsistemi.domain.PetRuntimeState;
import com.petsistemi.domain.RuntimeRepresentationType;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PetIdleSleepControllerTest {

    private AtomicLong clock;
    private AtomicReference<RuntimeConfigurationSnapshot> configRef;
    private RecordingRepresentation representation;
    private PetRepresentationRegistry representationRegistry;
    private PetIdleSleepController controller;

    private final UUID ownerId = UUID.randomUUID();
    private final UUID petId = UUID.randomUUID();
    private World world;
    private Player player;
    private Entity petEntity;
    private ActivePet active;

    private Location ownerLocation;
    private Location petLocation;

    @BeforeEach
    void setUp() {
        clock = new AtomicLong(1_000L);
        configRef = new AtomicReference<>(snapshot(features(true, 5)));
        representation = new RecordingRepresentation();
        representationRegistry = new PetRepresentationRegistry();
        representationRegistry.register(RuntimeRepresentationType.ITEM_DISPLAY, representation);

        PetDefinition def = new PetDefinition("wolf", "Kurt", List.of(), "DOG", false, false, false, false, true, true, 100, true, List.of());
        PetDefinitionRegistry defRegistry = new PetDefinitionRegistry() {
            @Override public Optional<PetDefinition> find(String id) { return Optional.of(def); }
            @Override public java.util.Collection<PetDefinition> getAll() { return List.of(def); }
            @Override public void reload() {}
        };

        controller = new PetIdleSleepController(configRef, defRegistry, representationRegistry, null, () -> clock.get());

        world = mock(World.class);
        ownerLocation = new Location(world, 10, 64, 10);
        petLocation = new Location(world, 11, 64, 10);

        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(ownerId);
        when(player.getWorld()).thenReturn(world);
        when(player.getLocation()).thenAnswer(inv -> ownerLocation);

        petEntity = mock(Entity.class);
        when(petEntity.isValid()).thenReturn(true);
        when(petEntity.getWorld()).thenReturn(world);
        when(petEntity.getLocation()).thenAnswer(inv -> petLocation);

        active = new ActivePet(petId, ownerId, "wolf", 1, UUID.randomUUID(), petEntity, PetRuntimeState.ACTIVE);
        active.setRepresentationType(RuntimeRepresentationType.ITEM_DISPLAY);
        active.setFollowMode(PetFollowMode.FOLLOW);
        active.setPetInstance(new PetInstance(petId, ownerId, "wolf", "Kurt", 1, 0, PetAvailabilityState.AVAILABLE, 1L, 1L));
    }

    private static PluginConfiguration.FeaturesConfiguration features(boolean idleEnabled, int idleSeconds) {
        return new PluginConfiguration.FeaturesConfiguration(false, false, false, false,
                idleEnabled, idleSeconds, false, false, 0.02, 1.5);
    }

    private static RuntimeConfigurationSnapshot snapshot(PluginConfiguration.FeaturesConfiguration features) {
        PluginConfiguration config = new PluginConfiguration(
                new PluginConfiguration.LimitsConfiguration(10),
                new PluginConfiguration.NamingConfiguration(3, 32, true, true),
                new PluginConfiguration.ProgressionConfiguration(true, 100),
                new PluginConfiguration.RuntimeConfiguration(1, 1.5, 3.0, 30.0, 1.4),
                new PluginConfiguration.DatabaseConfiguration(true, false, 5),
                new PluginConfiguration.GuiConfiguration("Pet Menüsü", 6),
                new PluginConfiguration.DiagnosticsConfiguration(100L),
                new PluginConfiguration.DefinitionConfiguration("KEEP_OLD_ON_ANY_ERROR"),
                features,
                "tr");
        return new RuntimeConfigurationSnapshot(config, null, null, 0L);
    }

    private void tick() {
        controller.tick(player, active, petEntity);
    }

    private static PetDefinition defWithStates(com.petsistemi.domain.PetStatesDefinition states) {
        return new PetDefinition("sleepy_cat", "Uyuyan Kedi", List.of(), "CAT", false, false, false, false, true, true, 100, true, List.of(),
                null, null, states);
    }

    private static PetDefinition defWithStatesAndTransforms() {
        com.petsistemi.domain.PetTransformDefinition night = new com.petsistemi.domain.PetTransformDefinition(
                new com.petsistemi.domain.PetTransformCondition(null, null, null,
                        com.petsistemi.domain.PetTimeOfDay.NIGHT, null),
                new com.petsistemi.domain.PetVisualOverride("SOUL_LANTERN", null, null, null, null, null, null, null));
        com.petsistemi.domain.PetStatesDefinition states = new com.petsistemi.domain.PetStatesDefinition(
                null,
                new com.petsistemi.domain.PetStateDefinition(100, com.petsistemi.domain.PetIdleAnimation.SLEEP));
        return new PetDefinition("sleepy_cat", "Uyuyan Kedi", List.of(), "CAT", false, false, false, false, true, true, 100, true, List.of(),
                null, null, states, List.of(night));
    }

    private PetIdleSleepController controllerWith(PetDefinition def) {
        PetDefinitionRegistry defRegistry = new PetDefinitionRegistry() {
            @Override public Optional<PetDefinition> find(String id) { return Optional.of(def); }
            @Override public java.util.Collection<PetDefinition> getAll() { return List.of(def); }
            @Override public void reload() {}
        };
        return new PetIdleSleepController(configRef, defRegistry, representationRegistry, null, () -> clock.get());
    }

    @Test
    void restsAfterIdleThreshold() {
        tick();
        assertFalse(active.isResting());
        assertTrue(representation.restCalls.isEmpty());

        clock.set(7_000L);
        tick();

        assertTrue(active.isResting());
        assertFalse(representation.restCalls.isEmpty());
        assertTrue(representation.restCalls.get(representation.restCalls.size() - 1));
    }

    @Test
    void wakesWhenOwnerMoves() {
        tick();
        clock.set(7_000L);
        tick();
        assertTrue(active.isResting());

        clock.set(8_000L);
        ownerLocation = new Location(world, 12, 64, 10);
        tick();

        assertFalse(active.isResting());
        assertFalse(representation.restCalls.get(representation.restCalls.size() - 1));
    }

    @Test
    void staysRestingWhileIdle() {
        tick();
        clock.set(7_000L);
        tick();
        clock.set(9_000L);
        tick();

        assertTrue(active.isResting());
        long restCalls = representation.restCalls.stream().filter(b -> b).count();
        assertEquals(1, restCalls, "rest state must be applied only on the transition");
    }

    @Test
    void doesNotRestWhenFeatureDisabled() {
        configRef.set(snapshot(features(false, 5)));
        tick();
        clock.set(7_000L);
        tick();

        assertFalse(active.isResting());
        assertTrue(representation.restCalls.isEmpty());
    }

    @Test
    void doesNotRestWhenFollowModeIsStay() {
        active.setFollowMode(PetFollowMode.STAY);
        tick();
        clock.set(7_000L);
        tick();

        assertFalse(active.isResting());
    }

    @Test
    void doesNotRestWhenPetIsFar() {
        petLocation = new Location(world, 25, 64, 10);
        tick();
        clock.set(7_000L);
        tick();

        assertFalse(active.isResting());
    }

    @Test
    void wakesWhenPetDriftsAway() {
        tick();
        clock.set(7_000L);
        tick();
        assertTrue(active.isResting());

        clock.set(8_000L);
        petLocation = new Location(world, 20, 64, 10);
        tick();

        assertFalse(active.isResting());
    }

    @Test
    void cleanupAllowsRestartingFromScratch() {
        tick();
        clock.set(7_000L);
        tick();
        assertTrue(active.isResting());

        controller.cleanup(ownerId);
        clock.set(10_000L);
        tick();
        assertFalse(active.isResting(), "fresh owner state must start awake");
    }

    @Test
    void nullEntityIsIgnored() {
        assertDoesNotThrow(() -> controller.tick(player, active, null));
        assertDoesNotThrow(() -> controller.tick(null, active, petEntity));
        assertFalse(active.isResting());
    }

    @Test
    void perPetIdleStateWorksWhenGlobalFeatureDisabled() {
        configRef.set(snapshot(features(false, 5)));
        controller = controllerWith(defWithStates(new com.petsistemi.domain.PetStatesDefinition(
                new com.petsistemi.domain.PetStateDefinition(0, com.petsistemi.domain.PetIdleAnimation.WALK),
                new com.petsistemi.domain.PetStateDefinition(100, com.petsistemi.domain.PetIdleAnimation.SLEEP))));

        tick();
        clock.set(5_500L);
        tick();
        assertFalse(active.isResting());

        clock.set(7_000L);
        tick();
        assertTrue(active.isResting(), "per-pet after-ticks (100 = 5000ms) must enable rest despite global feature off");
    }

    @Test
    void perPetAfterTicksOverridesGlobalIdleSeconds() {
        configRef.set(snapshot(features(true, 45)));
        controller = controllerWith(defWithStates(new com.petsistemi.domain.PetStatesDefinition(
                null,
                new com.petsistemi.domain.PetStateDefinition(20, com.petsistemi.domain.PetIdleAnimation.SIT))));

        tick();
        clock.set(2_000L);
        tick();
        assertTrue(active.isResting(), "20 ticks (1000ms) must beat the global 45s threshold");
    }

    @Test
    void perPetIdleWithoutAfterTicksUsesGlobalSeconds() {
        configRef.set(snapshot(features(true, 5)));
        controller = controllerWith(defWithStates(new com.petsistemi.domain.PetStatesDefinition(
                null,
                new com.petsistemi.domain.PetStateDefinition(0, com.petsistemi.domain.PetIdleAnimation.SLEEP))));

        tick();
        clock.set(3_000L);
        tick();
        assertFalse(active.isResting());

        clock.set(7_000L);
        tick();
        assertTrue(active.isResting());
    }

    @Test
    void noneAnimationDisablesRestForThatPet() {
        configRef.set(snapshot(features(true, 5)));
        controller = controllerWith(defWithStates(new com.petsistemi.domain.PetStatesDefinition(
                null,
                new com.petsistemi.domain.PetStateDefinition(10, com.petsistemi.domain.PetIdleAnimation.NONE))));

        tick();
        clock.set(7_000L);
        tick();
        assertFalse(active.isResting(), "IDLE animation NONE must keep the pet awake");
        assertTrue(representation.restCalls.isEmpty());
    }

    @Test
    void perPetStatesOnlyWithoutIdleFallsBackToGlobal() {
        configRef.set(snapshot(features(true, 5)));
        controller = controllerWith(defWithStates(new com.petsistemi.domain.PetStatesDefinition(
                new com.petsistemi.domain.PetStateDefinition(0, com.petsistemi.domain.PetIdleAnimation.WALK),
                null)));

        tick();
        clock.set(7_000L);
        tick();
        assertTrue(active.isResting(), "MOVING-only states must not disable the global idle-sleep");
    }

    @Test
    void restVisualUsesTransformedDefinitionWhenWired() {
        configRef.set(snapshot(features(false, 5)));
        PetDefinition def = defWithStatesAndTransforms();
        PetDefinitionRegistry defRegistry = new PetDefinitionRegistry() {
            @Override public Optional<PetDefinition> find(String id) { return Optional.of(def); }
            @Override public java.util.Collection<PetDefinition> getAll() { return List.of(def); }
            @Override public void reload() {}
        };
        PetTransformController transforms = new PetTransformController(defRegistry, representationRegistry);
        controller = new PetIdleSleepController(configRef, defRegistry, representationRegistry, null, () -> clock.get());
        controller.setTransformController(transforms);

        when(world.getTime()).thenReturn(14_000L);
        transforms.tick(player, active, petEntity);

        tick();
        clock.set(7_000L);
        tick();

        assertTrue(active.isResting());
        assertFalse(representation.restDefinitions.isEmpty());
        assertEquals("SOUL_LANTERN",
                representation.restDefinitions.get(representation.restDefinitions.size() - 1)
                        .representation().itemMaterial());
    }

    private static class RecordingRepresentation implements PetRepresentationController {
        final java.util.List<Boolean> restCalls = new java.util.ArrayList<>();
        final java.util.List<PetDefinition> restDefinitions = new java.util.ArrayList<>();

        @Override
        public Entity spawn(PetInstance pet, PetDefinition definition, Player owner) {
            return null;
        }

        @Override
        public void applyRestState(Entity primaryEntity, PetInstance pet, PetDefinition definition, boolean resting) {
            restCalls.add(resting);
            restDefinitions.add(definition);
        }

        @Override
        public void updateVisual(Entity primaryEntity, PetInstance pet, PetDefinition definition) {
        }

        @Override
        public void remove(Entity primaryEntity) {
        }

        @Override
        public boolean isValid(Entity primaryEntity) {
            return false;
        }
    }
}

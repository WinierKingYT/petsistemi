package com.petsistemi.runtime;

import com.petsistemi.definition.PetDefinitionRegistry;
import com.petsistemi.domain.PetAvailabilityState;
import com.petsistemi.domain.PetDefinition;
import com.petsistemi.domain.PetInstance;
import com.petsistemi.domain.PetOwnerState;
import com.petsistemi.domain.PetRepresentationDefinition;
import com.petsistemi.domain.PetRuntimeState;
import com.petsistemi.domain.PetTimeOfDay;
import com.petsistemi.domain.PetTransformCondition;
import com.petsistemi.domain.PetTransformDefinition;
import com.petsistemi.domain.PetVisualOverride;
import com.petsistemi.domain.PetWeather;
import com.petsistemi.domain.RuntimeRepresentationType;
import org.bukkit.Location;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.World;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PetTransformControllerTest {

    private final UUID ownerId = UUID.randomUUID();
    private final UUID petId = UUID.randomUUID();

    private World world;
    private Player player;
    private Entity petEntity;
    private ActivePet active;
    private PetTransformController controller;
    private RecordingVisual representation;

    private PetDefinition base;
    private PetTransformDefinition nightTransform;
    private PetTransformDefinition desertTransform;
    private PetTransformDefinition flyingTransform;

    @BeforeEach
    void setUp() {
        world = mock(World.class);
        when(world.getName()).thenReturn("world");
        when(world.getTime()).thenReturn(1_000L);

        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(ownerId);
        when(player.getWorld()).thenReturn(world);

        Block block = mock(Block.class);
        when(block.getBiome()).thenReturn(Biome.PLAINS);
        when(world.getBlockAt(any(Location.class))).thenReturn(block);
        when(player.getLocation()).thenReturn(new Location(world, 10, 64, 10));

        petEntity = mock(Entity.class);
        when(petEntity.isValid()).thenReturn(true);

        PetInstance instance = new PetInstance(petId, ownerId, "wisplight", "Wisplight", 1, 0,
                PetAvailabilityState.AVAILABLE, 1L, 1L);
        active = new ActivePet(petId, ownerId, "wisplight", 1, petEntity.getUniqueId(), petEntity, PetRuntimeState.ACTIVE);
        active.setPetInstance(instance);
        active.setRepresentationType(RuntimeRepresentationType.ITEM_DISPLAY);

        PetRepresentationDefinition rep = new PetRepresentationDefinition(
                RuntimeRepresentationType.ITEM_DISPLAY, null, false, false, true, true, false,
                "GLOWSTONE_DUST", null, new com.petsistemi.domain.PetVector3(0.7, 0.7, 0.7));

        nightTransform = new PetTransformDefinition(
                new PetTransformCondition(null, null, null, PetTimeOfDay.NIGHT, null),
                new PetVisualOverride("SOUL_LANTERN", null, null, null, null, null, true, null));

        desertTransform = new PetTransformDefinition(
                new PetTransformCondition(null, "DESERT", null, null, null),
                new PetVisualOverride("SAND", null, null, null, null, null, null, null));

        flyingTransform = new PetTransformDefinition(
                new PetTransformCondition(PetOwnerState.FLYING, null, null, null, null),
                new PetVisualOverride("FEATHER", null, null, null, null, null, null, null));

        base = new PetDefinition("wisplight", "Wisplight", List.of(), "WOLF",
                false, false, true, false, true, true, 100, true, List.of("{pet_name}"),
                rep, null, null, List.of(nightTransform, desertTransform, flyingTransform));

        representation = new RecordingVisual();
        PetRepresentationRegistry repRegistry = new PetRepresentationRegistry();
        repRegistry.register(RuntimeRepresentationType.ITEM_DISPLAY, representation);

        controller = new PetTransformController(registryOf(base), repRegistry);
    }

    private PetDefinitionRegistry registryOf(PetDefinition def) {
        return new PetDefinitionRegistry() {
            @Override public Optional<PetDefinition> find(String id) { return Optional.of(def); }
            @Override public java.util.Collection<PetDefinition> getAll() { return List.of(def); }
            @Override public void reload() {}
        };
    }

    private void tick() {
        controller.tick(player, active, petEntity);
    }

    @Test
    void appliesNightTransformWhenTimeIsNight() {
        when(world.getTime()).thenReturn(14_000L);
        tick();

        assertEquals(1, representation.visualCalls.size());
        PetDefinition rendered = representation.visualCalls.get(0);
        assertEquals("SOUL_LANTERN", rendered.representation().itemMaterial());
        assertTrue(rendered.representation().glowing());
        assertEquals(rendered, controller.activeDefinition(active));
    }

    @Test
    void revertsToBaseWhenConditionClears() {
        when(world.getTime()).thenReturn(14_000L);
        tick();
        assertEquals("SOUL_LANTERN", representation.visualCalls.get(0).representation().itemMaterial());

        when(world.getTime()).thenReturn(1_000L);
        tick();

        assertEquals(2, representation.visualCalls.size());
        assertEquals("GLOWSTONE_DUST", representation.visualCalls.get(1).representation().itemMaterial());
        assertEquals(base, controller.activeDefinition(active));
    }

    @Test
    void noMatchKeepsBaseDefinition() {
        tick();

        assertEquals(1, representation.visualCalls.size());
        assertEquals("GLOWSTONE_DUST", representation.visualCalls.get(0).representation().itemMaterial());
        assertEquals(base, controller.activeDefinition(active));
    }

    @Test
    void ownerStateConditionMatches() {
        tick();
        assertEquals("GLOWSTONE_DUST", representation.visualCalls.get(0).representation().itemMaterial());

        when(player.isFlying()).thenReturn(true);
        tick();

        assertEquals(2, representation.visualCalls.size());
        assertEquals("FEATHER", representation.visualCalls.get(1).representation().itemMaterial());
    }

    @Test
    void biomeConditionMatchesByName() {
        Block desertBlock = mock(Block.class);
        when(desertBlock.getBiome()).thenReturn(Biome.DESERT);
        when(world.getBlockAt(any(Location.class))).thenReturn(desertBlock);
        tick();

        assertEquals("SAND", representation.visualCalls.get(0).representation().itemMaterial());
    }

    @Test
    void worldConditionMatchesByName() {
        PetTransformDefinition otherWorld = new PetTransformDefinition(
                new PetTransformCondition(null, null, "world_nether", null, null),
                new PetVisualOverride("NETHERRACK", null, null, null, null, null, null, null));
        base = new PetDefinition("wisplight", "Wisplight", List.of(), "WOLF",
                false, false, true, false, true, true, 100, true, List.of("{pet_name}"),
                base.representation(), null, null, List.of(otherWorld));
        controller = new PetTransformController(registryOf(base), registryOfRep());

        tick();
        assertEquals("GLOWSTONE_DUST", representation.visualCalls.get(0).representation().itemMaterial());

        when(world.getName()).thenReturn("world_nether");
        tick();
        assertEquals("NETHERRACK", representation.visualCalls.get(1).representation().itemMaterial());
    }

    @Test
    void weatherConditionMatches() {
        PetTransformDefinition rainy = new PetTransformDefinition(
                new PetTransformCondition(null, null, null, null, PetWeather.RAIN),
                new PetVisualOverride("WET_SPONGE", null, null, null, null, null, null, null));
        base = new PetDefinition("wisplight", "Wisplight", List.of(), "WOLF",
                false, false, true, false, true, true, 100, true, List.of("{pet_name}"),
                base.representation(), null, null, List.of(rainy));
        controller = new PetTransformController(registryOf(base), registryOfRep());

        tick();
        assertEquals("GLOWSTONE_DUST", representation.visualCalls.get(0).representation().itemMaterial());

        when(world.hasStorm()).thenReturn(true);
        tick();
        assertEquals("WET_SPONGE", representation.visualCalls.get(1).representation().itemMaterial());
    }

    @Test
    void restingPetGetsRestStateWithDerivedDefinition() {
        when(world.getTime()).thenReturn(14_000L);
        active.setResting(true);
        tick();

        assertFalse(representation.restCalls.isEmpty());
        assertEquals("SOUL_LANTERN", representation.restCalls.get(representation.restCalls.size() - 1).representation().itemMaterial());
    }

    @Test
    void petWithoutTransformsUsesBaseAndCleansUp() {
        base = new PetDefinition("wolf", "Wolf", List.of(), "WOLF",
                false, false, true, false, true, true, 100, true, List.of("{pet_name}"),
                base.representation(), null, null, null);
        controller = new PetTransformController(registryOf(base), registryOfRep());

        tick();
        assertEquals(base, controller.activeDefinition(active));

        controller.cleanup(ownerId);
        assertEquals(base, controller.activeDefinition(active), "pets without transforms always resolve to base");
    }

    @Test
    void transformTickWithoutEntityIsIgnored() {
        assertDoesNotThrow(() -> controller.tick(player, active, null));
        assertDoesNotThrow(() -> controller.tick(null, active, petEntity));
        assertTrue(representation.visualCalls.isEmpty());
    }

    private PetRepresentationRegistry registryOfRep() {
        PetRepresentationRegistry repRegistry = new PetRepresentationRegistry();
        repRegistry.register(RuntimeRepresentationType.ITEM_DISPLAY, representation);
        return repRegistry;
    }

    private static class RecordingVisual implements PetRepresentationController {
        final java.util.List<PetDefinition> visualCalls = new java.util.ArrayList<>();
        final java.util.List<PetDefinition> restCalls = new java.util.ArrayList<>();

        @Override
        public Entity spawn(PetInstance pet, PetDefinition definition, Player owner) {
            return null;
        }

        @Override
        public void updateVisual(Entity primaryEntity, PetInstance pet, PetDefinition definition) {
            visualCalls.add(definition);
        }

        @Override
        public void applyRestState(Entity primaryEntity, PetInstance pet, PetDefinition definition, boolean resting) {
            restCalls.add(definition);
        }

        @Override
        public void remove(Entity primaryEntity) {
        }

        @Override
        public boolean isValid(Entity primaryEntity) {
            return true;
        }
    }
}

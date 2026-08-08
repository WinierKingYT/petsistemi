package com.petsistemi.runtime;

import com.petsistemi.domain.PetAvailabilityState;
import com.petsistemi.domain.PetDefinition;
import com.petsistemi.domain.PetInstance;
import com.petsistemi.domain.PetRepresentationDefinition;
import com.petsistemi.domain.PetVector3;
import com.petsistemi.domain.RuntimeRepresentationType;
import com.petsistemi.domain.animation.PetAnimationState;
import com.petsistemi.domain.visual.PetVisualGraphDefinition;
import com.petsistemi.domain.visual.PetVisualNodeDefinition;
import com.petsistemi.domain.visual.PetVisualTransform;
import com.petsistemi.runtime.animation.PetAnimationTransition;
import com.petsistemi.runtime.visual.PetVisualHandle;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CompositePetRepresentationTest {

    @Test
    void spawnsSynchronizesDelegatesLifecycleAndRemovesEveryComponent() {
        World world = mock(World.class);
        Entity body = entity(world, new Location(world, 10, 64, 20, 90f, 0f));
        Entity aura = entity(world, new Location(world, 0, 0, 0));
        List<String> removals = new ArrayList<>();
        RecordingController bodyController = new RecordingController("body", body, removals);
        RecordingController auraController = new RecordingController("aura", aura, removals);
        PetRepresentationRegistry registry = new PetRepresentationRegistry();
        registry.register(RuntimeRepresentationType.ITEM_DISPLAY, bodyController);
        registry.register(RuntimeRepresentationType.PARTICLE, auraController);
        CompositePetRepresentation composite = new CompositePetRepresentation(registry);
        registry.register(RuntimeRepresentationType.COMPOSITE, composite);

        PetDefinition definition = definition();
        Player owner = mock(Player.class);
        PetInstance pet = pet();
        PetVisualHandle handle = composite.spawnVisual(pet, definition, owner);

        assertEquals("body", handle.rootComponentId());
        assertEquals(List.of("body", "aura"), handle.components().stream().map(component -> component.id()).toList());
        ArgumentCaptor<Location> target = ArgumentCaptor.forClass(Location.class);
        verify(aura).teleport(target.capture());
        assertEquals(10.0, target.getValue().getX(), 0.0001);
        assertEquals(66.0, target.getValue().getY(), 0.0001);
        assertEquals(21.0, target.getValue().getZ(), 0.0001);

        composite.tickVisualHandle(handle, pet, definition, owner);
        composite.updateVisualHandle(handle, pet, definition);
        composite.applyRestStateHandle(handle, pet, definition, true);
        composite.applyAnimationHandle(handle, pet, definition,
                new PetAnimationTransition(PetAnimationState.IDLE, null, PetAnimationState.SLEEPING, null));

        assertEquals(1, bodyController.ticks);
        assertEquals(1, auraController.ticks);
        assertEquals(1, bodyController.updates);
        assertEquals(1, auraController.updates);
        assertEquals(1, bodyController.rests);
        assertEquals(1, auraController.rests);
        assertEquals(1, bodyController.animations);
        assertEquals(1, auraController.animations);

        composite.removeVisualHandle(handle);
        assertEquals(List.of("aura", "body"), removals, "children must be removed before the root");
    }

    @Test
    void spawnFailureRollsBackAlreadySpawnedComponents() {
        World world = mock(World.class);
        Entity body = entity(world, new Location(world, 0, 0, 0));
        List<String> removals = new ArrayList<>();
        PetRepresentationRegistry registry = new PetRepresentationRegistry();
        registry.register(RuntimeRepresentationType.ITEM_DISPLAY,
                new RecordingController("body", body, removals));
        CompositePetRepresentation composite = new CompositePetRepresentation(registry);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> composite.spawnVisual(pet(), definition(), mock(Player.class)));

        assertTrue(error.getMessage().contains("particle"));
        assertEquals(List.of("body"), removals);
    }

    private static PetDefinition definition() {
        PetRepresentationDefinition body = PetRepresentationDefinition.display(
                RuntimeRepresentationType.ITEM_DISPLAY, "BLAZE_POWDER", 1201, PetVector3.ONE);
        PetRepresentationDefinition aura = new PetRepresentationDefinition(
                RuntimeRepresentationType.PARTICLE, "MARKER", false, false, true, true, false,
                null, null, PetVector3.ONE, "FLAME", 6, 0.2, 0.0, 0, null);
        PetVisualGraphDefinition graph = new PetVisualGraphDefinition("body", List.of(
                new PetVisualNodeDefinition("aura", "body", aura,
                        new PetVisualTransform(new PetVector3(1, 2, 0), PetVector3.ZERO,
                                new PetVector3(0.5, 0.5, 0.5))),
                new PetVisualNodeDefinition("body", null, body, PetVisualTransform.IDENTITY)
        ));
        return PetDefinition.builder("fire_familiar", "Fire Familiar")
                .representation(PetRepresentationDefinition.composite(graph)).build();
    }

    private static PetInstance pet() {
        UUID owner = UUID.randomUUID();
        return new PetInstance(UUID.randomUUID(), owner, "fire_familiar", "Fire", 1, 0,
                PetAvailabilityState.AVAILABLE, 0, 0);
    }

    private static Entity entity(World world, Location location) {
        Entity entity = mock(Entity.class);
        when(entity.getUniqueId()).thenReturn(UUID.randomUUID());
        when(entity.isValid()).thenReturn(true);
        when(entity.isDead()).thenReturn(false);
        when(entity.getWorld()).thenReturn(world);
        when(entity.getLocation()).thenReturn(location);
        return entity;
    }

    private static final class RecordingController implements PetRepresentationController {
        private final String id;
        private final Entity entity;
        private final List<String> removals;
        private int ticks;
        private int updates;
        private int rests;
        private int animations;

        private RecordingController(String id, Entity entity, List<String> removals) {
            this.id = id;
            this.entity = entity;
            this.removals = removals;
        }

        @Override public Entity spawn(PetInstance pet, PetDefinition definition, Player owner) { return entity; }
        @Override public void tickVisual(Entity entity, PetInstance pet, PetDefinition definition, Player owner) { ticks++; }
        @Override public void updateVisual(Entity entity, PetInstance pet, PetDefinition definition) { updates++; }
        @Override public void applyRestState(Entity entity, PetInstance pet, PetDefinition definition, boolean resting) { rests++; }
        @Override public void applyAnimation(Entity entity, PetInstance pet, PetDefinition definition,
                                             PetAnimationTransition transition) { animations++; }
        @Override public void remove(Entity entity) { removals.add(id); }
        @Override public boolean isValid(Entity entity) { return entity != null && entity.isValid(); }
    }
}

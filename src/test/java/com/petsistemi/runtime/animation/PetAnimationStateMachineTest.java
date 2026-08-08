package com.petsistemi.runtime.animation;

import com.petsistemi.domain.PetAvailabilityState;
import com.petsistemi.domain.PetDefinition;
import com.petsistemi.domain.PetInstance;
import com.petsistemi.domain.PetRuntimeState;
import com.petsistemi.domain.PetStateDefinition;
import com.petsistemi.domain.PetStatesDefinition;
import com.petsistemi.domain.RuntimeRepresentationType;
import com.petsistemi.domain.animation.PetAnimationClipDefinition;
import com.petsistemi.domain.animation.PetAnimationState;
import com.petsistemi.runtime.ActivePet;
import com.petsistemi.runtime.PetRepresentationController;
import com.petsistemi.runtime.PetRepresentationRegistry;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PetAnimationStateMachineTest {
    private final UUID petId = UUID.randomUUID();
    private final UUID ownerId = UUID.randomUUID();
    private final RecordingRepresentation representation = new RecordingRepresentation();
    private final PetRepresentationRegistry registry = new PetRepresentationRegistry();
    private World world;
    private Entity entity;
    private Player owner;
    private ActivePet active;
    private Location location;
    private PetAnimationStateMachine machine;

    @BeforeEach
    void setUp() {
        registry.register(RuntimeRepresentationType.ITEM_DISPLAY, representation);
        machine = new PetAnimationStateMachine(registry);
        world = mock(World.class);
        location = new Location(world, 0, 64, 0);
        entity = mock(Entity.class);
        when(entity.isValid()).thenReturn(true);
        when(entity.getLocation()).thenAnswer(ignored -> location);
        owner = mock(Player.class);
        active = new ActivePet(petId, ownerId, "test", 1, UUID.randomUUID(), entity, PetRuntimeState.ACTIVE);
        active.setRepresentationType(RuntimeRepresentationType.ITEM_DISPLAY);
        active.setPetInstance(new PetInstance(petId, ownerId, "test", "Test", 1, 0,
                PetAvailabilityState.AVAILABLE, 1L, 1L));
    }

    @Test
    void samplesIdleMovingSprintingAndSleepingThroughOneInterface() {
        PetDefinition definition = PetDefinition.builder("test", "Test").build();

        machine.tick(active, entity, owner, definition);
        assertEquals(PetAnimationState.IDLE, active.getAnimationState());

        location = new Location(world, 1, 64, 0);
        machine.tick(active, entity, owner, definition);
        assertEquals(PetAnimationState.MOVING, active.getAnimationState());

        when(owner.isSprinting()).thenReturn(true);
        location = new Location(world, 2, 64, 0);
        machine.tick(active, entity, owner, definition);
        assertEquals(PetAnimationState.SPRINTING, active.getAnimationState());

        active.setResting(true);
        machine.tick(active, entity, owner, definition);
        assertEquals(PetAnimationState.SLEEPING, active.getAnimationState());
        assertTrue(representation.resting);
        assertEquals(List.of(PetAnimationState.IDLE, PetAnimationState.MOVING,
                PetAnimationState.SPRINTING, PetAnimationState.SLEEPING), representation.states());
    }

    @Test
    void configuredNamedClipCarriesPriorityAndBlendMetadata() {
        NamespacedKey clipKey = new NamespacedKey("example", "fast_run");
        PetStateDefinition sprinting = new PetStateDefinition(0, null, clipKey, 42, 3, 5, true);
        PetDefinition definition = PetDefinition.builder("test", "Test")
                .states(new PetStatesDefinition(null, null, sprinting, null, null)).build();

        machine.updateBaseState(active, entity, definition, PetAnimationState.SPRINTING);

        PetAnimationClipDefinition clip = active.getAnimationClip();
        assertEquals(clipKey, clip.key());
        assertEquals(42, clip.priority());
        assertEquals(3, clip.blendInTicks());
        assertEquals(5, clip.blendOutTicks());
        assertTrue(clip.loop());
    }

    @Test
    void higherPriorityTransientBlocksLowerPriorityAndResumesLatestBaseState() {
        PetDefinition definition = PetDefinition.builder("test", "Test").build();
        machine.updateBaseState(active, entity, definition, PetAnimationState.IDLE);
        PetAnimationClipDefinition attack = new PetAnimationClipDefinition(
                new NamespacedKey("petsistemi", "bite"), 100, 1, 2, false);
        PetAnimationClipDefinition emote = new PetAnimationClipDefinition(
                new NamespacedKey("petsistemi", "wave"), 20, 2, 2, false);

        assertTrue(machine.playTransient(active, entity, definition, PetAnimationState.ATTACKING, attack));
        assertFalse(machine.playTransient(active, entity, definition, PetAnimationState.ATTACKING, emote));
        machine.updateBaseState(active, entity, definition, PetAnimationState.MOVING);
        assertEquals(PetAnimationState.ATTACKING, active.getAnimationState());

        machine.finishTransient(active, entity, definition);
        assertEquals(PetAnimationState.MOVING, active.getAnimationState());
        assertEquals("walk", active.getAnimationClip().key().getKey());
    }

    private static final class RecordingRepresentation implements PetRepresentationController {
        private final List<PetAnimationTransition> transitions = new ArrayList<>();
        private boolean resting;

        List<PetAnimationState> states() {
            return transitions.stream().map(PetAnimationTransition::state).toList();
        }

        @Override
        public void applyAnimation(Entity primaryEntity, PetInstance pet, PetDefinition definition,
                                   PetAnimationTransition transition) {
            transitions.add(transition);
            PetRepresentationController.super.applyAnimation(primaryEntity, pet, definition, transition);
        }

        @Override
        public void applyRestState(Entity primaryEntity, PetInstance pet, PetDefinition definition, boolean resting) {
            this.resting = resting;
        }

        @Override public Entity spawn(PetInstance pet, PetDefinition definition, Player owner) { return null; }
        @Override public void updateVisual(Entity primaryEntity, PetInstance pet, PetDefinition definition) {}
        @Override public void remove(Entity primaryEntity) {}
        @Override public boolean isValid(Entity primaryEntity) { return true; }
    }
}

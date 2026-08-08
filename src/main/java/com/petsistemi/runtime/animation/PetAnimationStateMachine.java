package com.petsistemi.runtime.animation;

import com.petsistemi.domain.PetDefinition;
import com.petsistemi.domain.PetIdleAnimation;
import com.petsistemi.domain.PetStateDefinition;
import com.petsistemi.domain.PetStatesDefinition;
import com.petsistemi.domain.animation.PetAnimationClipDefinition;
import com.petsistemi.domain.animation.PetAnimationState;
import com.petsistemi.runtime.ActivePet;
import com.petsistemi.runtime.PetRepresentationController;
import com.petsistemi.runtime.PetRepresentationRegistry;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Provider-independent state machine for vanilla, display and future model providers.
 * Base locomotion states can be temporarily overridden by a priority-bearing clip
 * (for example ATTACKING) and resume when that transient clip completes.
 */
public final class PetAnimationStateMachine {
    private static final double MOVE_EPSILON_SQUARED = 0.0025;

    private final PetRepresentationRegistry representationRegistry;
    private final Map<UUID, Location> lastPositions = new HashMap<>();
    private final Map<UUID, PetAnimationState> baseStates = new HashMap<>();
    private final Map<UUID, PetAnimationState> currentStates = new HashMap<>();
    private final Map<UUID, PetAnimationClipDefinition> transientClips = new HashMap<>();

    public PetAnimationStateMachine(PetRepresentationRegistry representationRegistry) {
        this.representationRegistry = representationRegistry;
    }

    /** Samples actual pet movement and drives IDLE/MOVING/SPRINTING/SLEEPING. */
    public synchronized void tick(ActivePet active, Entity entity, Player owner, PetDefinition definition) {
        if (active == null || entity == null || !entity.isValid()) return;
        UUID petId = active.getPetId();
        Location current = entity.getLocation();
        Location previous = lastPositions.put(petId, current != null ? current.clone() : null);

        PetAnimationState desired;
        if (active.isResting()) {
            desired = PetAnimationState.SLEEPING;
        } else if (moved(previous, current)) {
            desired = owner != null && owner.isSprinting()
                    ? PetAnimationState.SPRINTING : PetAnimationState.MOVING;
        } else {
            desired = PetAnimationState.IDLE;
        }
        updateBaseState(active, entity, definition, desired);
    }

    /** Changes the persistent/base state; active transient clips retain control. */
    public synchronized void updateBaseState(ActivePet active, Entity entity, PetDefinition definition,
                                             PetAnimationState state) {
        if (active == null || state == null) return;
        baseStates.put(active.getPetId(), state);
        if (!transientClips.containsKey(active.getPetId())) {
            apply(active, entity, definition, state, resolveClip(definition, state));
        }
    }

    /**
     * Attempts to play a transient clip. A lower-priority request cannot interrupt
     * the currently active transient clip.
     */
    public synchronized boolean playTransient(ActivePet active, Entity entity, PetDefinition definition,
                                              PetAnimationState state, PetAnimationClipDefinition clip) {
        if (active == null || state == null || clip == null) return false;
        PetAnimationClipDefinition current = transientClips.get(active.getPetId());
        if (current != null && current.priority() > clip.priority()) return false;
        transientClips.put(active.getPetId(), clip);
        apply(active, entity, definition, state, clip);
        return true;
    }

    /** Finishes a transient clip and resumes the latest sampled base state. */
    public synchronized void finishTransient(ActivePet active, Entity entity, PetDefinition definition) {
        if (active == null || transientClips.remove(active.getPetId()) == null) return;
        PetAnimationState base = baseStates.getOrDefault(active.getPetId(), PetAnimationState.IDLE);
        apply(active, entity, definition, base, resolveClip(definition, base));
    }

    public synchronized Optional<PetAnimationState> currentState(UUID petId) {
        return petId == null ? Optional.empty() : Optional.ofNullable(currentStates.get(petId));
    }

    public synchronized void cleanup(UUID petId) {
        if (petId == null) return;
        lastPositions.remove(petId);
        baseStates.remove(petId);
        currentStates.remove(petId);
        transientClips.remove(petId);
    }

    private void apply(ActivePet active, Entity entity, PetDefinition definition,
                       PetAnimationState state, PetAnimationClipDefinition clip) {
        PetAnimationState previousState = active.getAnimationState();
        PetAnimationClipDefinition previousClip = active.getAnimationClip();
        if (state == previousState && clip.equals(previousClip)) return;

        PetRepresentationController controller = representationRegistry != null
                ? representationRegistry.get(active.getRepresentationKey()) : null;
        PetAnimationTransition transition = new PetAnimationTransition(previousState, previousClip, state, clip);
        if (controller != null && entity != null) {
            controller.applyAnimation(entity, active.getPetInstance(), definition, transition);
        }
        active.setAnimationState(state);
        active.setAnimationClip(clip);
        currentStates.put(active.getPetId(), state);
    }

    private static PetAnimationClipDefinition resolveClip(PetDefinition definition, PetAnimationState state) {
        PetStatesDefinition states = definition != null ? definition.states() : null;
        PetStateDefinition configured = states != null ? states.definition(state) : null;
        if (configured == null) configured = new PetStateDefinition(0, PetIdleAnimation.NONE);
        return configured.resolveClip(state);
    }

    private static boolean moved(Location previous, Location current) {
        if (previous == null || current == null) return false;
        if (previous.getWorld() == null || current.getWorld() == null
                || !previous.getWorld().equals(current.getWorld())) return true;
        return previous.distanceSquared(current) > MOVE_EPSILON_SQUARED;
    }
}

package com.petsistemi.runtime;

import com.petsistemi.config.PluginConfiguration;
import com.petsistemi.config.RuntimeConfigurationSnapshot;
import com.petsistemi.definition.PetDefinitionRegistry;
import com.petsistemi.domain.PetDefinition;
import com.petsistemi.domain.PetFollowMode;
import com.petsistemi.domain.PetIdleAnimation;
import com.petsistemi.domain.PetStateDefinition;
import com.petsistemi.domain.PetStatesDefinition;
import com.petsistemi.domain.RuntimeRepresentationType;
import com.petsistemi.domain.animation.PetAnimationState;
import com.petsistemi.runtime.animation.PetAnimationStateMachine;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Detects owner idleness and drives the pet's rest (idle/sleep) visual state:
 * the pet rests when the owner stands still for the idle threshold while the
 * pet is close by, and wakes up as soon as the owner moves (or the pet drifts away).
 * Enabled by the global {@code features.idle-sleep.enabled} flag, or per pet via
 * the {@code states.IDLE} section (which overrides the idle threshold with its own
 * {@code after-ticks}).
 */
public class PetIdleSleepController {

    private static final double WAKE_DISTANCE = 3.0;
    private static final double MOVE_EPSILON = 0.05;

    private final AtomicReference<RuntimeConfigurationSnapshot> configSnapshot;
    private final PetDefinitionRegistry definitionRegistry;
    private final PetRepresentationRegistry representationRegistry;
    private final PetReactionEngine reactionEngine;

    private volatile PetTransformController transformController;
    private volatile PetAnimationStateMachine animationStateMachine;

    private final Map<UUID, Location> lastOwnerPosition = new HashMap<>();
    private final Map<UUID, Long> lastMoveTime = new HashMap<>();
    private final Set<UUID> restingOwners = new HashSet<>();
    private final java.util.function.LongSupplier clock;

    public PetIdleSleepController(AtomicReference<RuntimeConfigurationSnapshot> configSnapshot,
                                  PetDefinitionRegistry definitionRegistry,
                                  PetRepresentationRegistry representationRegistry,
                                  PetReactionEngine reactionEngine) {
        this(configSnapshot, definitionRegistry, representationRegistry, reactionEngine, System::currentTimeMillis);
    }

    /** Test-friendly constructor with an injectable clock. */
    public PetIdleSleepController(AtomicReference<RuntimeConfigurationSnapshot> configSnapshot,
                                  PetDefinitionRegistry definitionRegistry,
                                  PetRepresentationRegistry representationRegistry,
                                  PetReactionEngine reactionEngine,
                                  java.util.function.LongSupplier clock) {
        this.configSnapshot = configSnapshot;
        this.definitionRegistry = definitionRegistry;
        this.representationRegistry = representationRegistry;
        this.reactionEngine = reactionEngine;
        this.clock = clock;
    }

    /** Wires the transform controller so rest visuals use the transformed definition. */
    public void setTransformController(PetTransformController transformController) {
        this.transformController = transformController;
    }

    public void setAnimationStateMachine(PetAnimationStateMachine animationStateMachine) {
        this.animationStateMachine = animationStateMachine;
    }

    /** Called once per pet per tick from the coordinator. */
    public void tick(Player owner, ActivePet active, Entity entity) {
        if (owner == null || active == null || entity == null || !entity.isValid()) {
            return;
        }
        UUID ownerId = owner.getUniqueId();

        PetDefinition definition = resolveDefinition(active);
        PetStatesDefinition states = definition != null ? definition.states() : null;
        PetStateDefinition idleState = states != null && states.sleeping() != null
                ? states.sleeping() : (states != null ? states.idle() : null);

        PluginConfiguration.FeaturesConfiguration features = features();
        boolean enabled = idleState != null || (features != null && features.idleSleepEnabled());
        if (!enabled || (idleState != null && idleState.animation() == PetIdleAnimation.NONE
                && idleState.clip() == null)) {
            wake(ownerId, active, entity);
            rememberMove(owner, true);
            return;
        }

        Location now = owner.getLocation();
        Location prev = lastOwnerPosition.get(ownerId);
        boolean moved = prev == null
                || !sameWorld(prev, now)
                || prev.distanceSquared(now) > MOVE_EPSILON * MOVE_EPSILON;
        if (moved) {
            rememberMove(owner, true);
            wake(ownerId, active, entity);
            return;
        }

        if (active.getFollowMode() != PetFollowMode.FOLLOW) {
            wake(ownerId, active, entity);
            return;
        }

        boolean resting = restingOwners.contains(ownerId);
        long idleMs = clock.getAsLong() - lastMoveTime.getOrDefault(ownerId, clock.getAsLong());
        long idleThresholdMs = thresholdMs(idleState, features);

        if (resting) {
            // Wake if the pet drifted too far from the owner
            if (sameWorld(entity.getLocation(), now) && entity.getLocation().distanceSquared(now) > WAKE_DISTANCE * WAKE_DISTANCE) {
                wake(ownerId, active, entity);
            }
            return;
        }

        if (idleMs >= idleThresholdMs && sameWorld(entity.getLocation(), now)
                && entity.getLocation().distanceSquared(now) <= WAKE_DISTANCE * WAKE_DISTANCE) {
            restingOwners.add(ownerId);
            active.setResting(true);
            applyRest(active, entity, definition, true);
            if (reactionEngine != null) {
                reactionEngine.playRestStart(entity, definition);
            }
        }
    }

    /** Frees per-owner state when the pet is despawned or the owner disconnects. */
    public void cleanup(UUID ownerId) {
        if (ownerId == null) return;
        restingOwners.remove(ownerId);
        lastOwnerPosition.remove(ownerId);
        lastMoveTime.remove(ownerId);
    }

    private void wake(UUID ownerId, ActivePet active, Entity entity) {
        if (restingOwners.remove(ownerId) || (active != null && active.isResting())) {
            if (active != null) {
                active.setResting(false);
            }
            applyRest(active, entity, resolveDefinition(active), false);
            if (reactionEngine != null) {
                reactionEngine.playWake(entity, resolveDefinition(active));
            }
        }
    }

    private void applyRest(ActivePet active, Entity entity, PetDefinition definition, boolean resting) {
        PetAnimationStateMachine animations = animationStateMachine;
        if (animations != null) {
            animations.updateBaseState(active, entity, definition,
                    resting ? PetAnimationState.SLEEPING : PetAnimationState.IDLE);
            return;
        }
        PetRepresentationController rep = active != null && representationRegistry != null
                ? representationRegistry.get(active.getRepresentationKey()) : null;
        if (rep == null) return;
        if (definition != null) {
            rep.applyRestState(entity, active.getPetInstance(), definition, resting);
        }
    }

    private PetDefinition resolveDefinition(ActivePet active) {
        if (definitionRegistry == null || active == null || active.getDefinitionId() == null) {
            return null;
        }
        PetDefinition base = definitionRegistry.find(active.getDefinitionId()).orElse(null);
        PetTransformController transforms = transformController;
        if (transforms == null || base == null) {
            return base;
        }
        PetDefinition derived = transforms.activeDefinition(active);
        return derived != null ? derived : base;
    }

    private long thresholdMs(PetStateDefinition idleState, PluginConfiguration.FeaturesConfiguration features) {
        if (idleState != null && idleState.afterTicks() > 0) {
            return (long) idleState.afterTicks() * 50L;
        }
        long seconds = features != null ? features.idleSleepSeconds() : 45L;
        return Math.max(5L, seconds) * 1000L;
    }

    private PluginConfiguration.FeaturesConfiguration features() {
        if (configSnapshot == null || configSnapshot.get() == null || configSnapshot.get().configuration() == null) {
            return null;
        }
        return configSnapshot.get().configuration().features();
    }

    private void rememberMove(Player owner, boolean storePosition) {
        UUID ownerId = owner.getUniqueId();
        lastMoveTime.put(ownerId, clock.getAsLong());
        if (storePosition) {
            lastOwnerPosition.put(ownerId, owner.getLocation());
        }
    }

    private static boolean sameWorld(Location a, Location b) {
        return a.getWorld() != null && b.getWorld() != null && a.getWorld().equals(b.getWorld());
    }
}

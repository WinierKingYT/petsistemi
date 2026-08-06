package com.petsistemi.runtime;

import com.petsistemi.domain.PetFollowMode;
import com.petsistemi.domain.PetMovementDefinition;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Roam-near-owner movement: the pet wanders freely within a radius around the owner,
 * picking a new random target when it reaches the current one (or after a stay
 * period). Teleports back to the owner's side when too far away.
 */
public class RoamNearOwnerMovement implements PetMovementController {

    private static final double DEFAULT_RADIUS = 4.0;
    private static final double DEFAULT_SPEED = 0.12;
    private static final double DEFAULT_TELEPORT_DISTANCE = 24.0;
    private static final double REACH_DISTANCE = 0.8;
    private static final int STAY_TICKS_MIN = 40;
    private static final int STAY_TICKS_MAX = 120;

    private final Map<UUID, RoamState> states = new HashMap<>();

    public RoamNearOwnerMovement() {
        this(new Random());
    }

    /** Test-friendly constructor with an injectable random source. */
    public RoamNearOwnerMovement(Random random) {
        this.random = random;
    }

    private final Random random;

    private static final class RoamState {
        Location target;
        int ticksUntilNextPick;
    }

    @Override
    public void initialize(ActivePet activePet, Entity entity, Player owner) {
        states.put(activePet.getPetId(), new RoamState());
    }

    @Override
    public void tick(ActivePet activePet, Entity entity, Player owner) {
        if (entity == null || !entity.isValid() || owner == null || !owner.isOnline()) {
            return;
        }
        if (activePet.getFollowMode() != PetFollowMode.FOLLOW) {
            return;
        }

        PetMovementDefinition mov = activePet.getMovementDefinition();
        double radius = mov != null && mov.followDistance() > 0.0 ? mov.followDistance() : DEFAULT_RADIUS;
        double speed = mov != null && mov.followSpeed() > 0.0 ? mov.followSpeed() : DEFAULT_SPEED;
        double teleportDistance = mov != null && mov.teleportDistance() > 0.0 ? mov.teleportDistance() : DEFAULT_TELEPORT_DISTANCE;

        UUID petId = activePet.getPetId();
        RoamState state = states.computeIfAbsent(petId, k -> new RoamState());

        Location ownerLoc = owner.getLocation();
        Location petLoc = entity.getLocation();

        if (!petLoc.getWorld().equals(ownerLoc.getWorld())) {
            state.target = null;
            FlyingFollowMovement.smoothTeleport(entity, ownerLoc.clone());
            return;
        }

        if (petLoc.distanceSquared(ownerLoc) > teleportDistance * teleportDistance) {
            state.target = null;
            state.ticksUntilNextPick = 0;
            FlyingFollowMovement.smoothTeleport(entity, ownerLoc.clone());
            return;
        }

        Location target = state.target;
        if (target == null || state.ticksUntilNextPick <= 0
                || petLoc.distanceSquared(target) <= REACH_DISTANCE * REACH_DISTANCE) {
            state.target = randomTarget(ownerLoc, radius);
            state.ticksUntilNextPick = STAY_TICKS_MIN + random.nextInt(STAY_TICKS_MAX - STAY_TICKS_MIN + 1);
            target = state.target;
        }
        state.ticksUntilNextPick--;

        Location next = petLoc.clone().add(target.toVector().subtract(petLoc.toVector())
                .normalize().multiply(speed));
        next.setYaw(ownerLoc.getYaw());
        FlyingFollowMovement.smoothTeleport(entity, next);
    }

    /** Pure math: a random point on the horizontal disc around the owner (from the injected random). */
    Location randomTarget(Location ownerLoc, double radius) {
        if (ownerLoc.getWorld() == null || radius <= 0.0) {
            return ownerLoc.clone();
        }
        double angle = random.nextDouble() * 2.0 * Math.PI;
        double distance = Math.sqrt(random.nextDouble()) * radius;
        return ownerLoc.clone().add(Math.cos(angle) * distance, 0.0, Math.sin(angle) * distance);
    }

    @Override
    public void remove(ActivePet activePet, Entity entity) {
        if (activePet != null) {
            states.remove(activePet.getPetId());
        }
    }
}

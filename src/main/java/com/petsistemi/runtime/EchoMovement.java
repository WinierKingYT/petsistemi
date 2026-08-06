package com.petsistemi.runtime;

import com.petsistemi.domain.PetFollowMode;
import com.petsistemi.domain.PetMovementDefinition;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Echo movement: unlike {@link TrailMovement} (which chases the oldest point while
 * new points keep being appended), the pet <b>consumes</b> the recorded path points —
 * it replays the owner's recent footsteps at its own pace and waits when the trail
 * runs out. Produces a delayed "ghost" that walks the owner's exact path.
 */
public class EchoMovement implements PetMovementController {

    private static final double ECHO_SPACING = 0.5;
    private static final double DEFAULT_TRAIL_LENGTH = 8.0;
    private static final double DEFAULT_TELEPORT_DISTANCE = 30.0;
    private static final double REACH_DISTANCE = 0.6;
    private static final double ADVANCE_SPEED = 0.35;

    private final Map<UUID, Deque<Location>> echoes = new HashMap<>();

    @Override
    public void initialize(ActivePet activePet, Entity entity, Player owner) {
        echoes.put(activePet.getPetId(), new ArrayDeque<>());
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
        double trailLength = mov != null && mov.followDistance() > 0.0 ? mov.followDistance() : DEFAULT_TRAIL_LENGTH;
        double teleportDistance = mov != null && mov.teleportDistance() > 0.0 ? mov.teleportDistance() : DEFAULT_TELEPORT_DISTANCE;

        UUID petId = activePet.getPetId();
        Deque<Location> path = echoes.computeIfAbsent(petId, k -> new ArrayDeque<>());

        Location ownerLoc = owner.getLocation();
        Location petLoc = entity.getLocation();

        if (!petLoc.getWorld().equals(ownerLoc.getWorld())) {
            path.clear();
            FlyingFollowMovement.smoothTeleport(entity, ownerLoc.clone());
            return;
        }

        // Keep recording the owner's path while the pet replays it.
        TrailMovement.pushOwnerPosition(path, ownerLoc, ECHO_SPACING, trailLength + REACH_DISTANCE * 4.0);
        if (path.isEmpty()) {
            return;
        }

        Location target = path.peekFirst();
        double distSq = petLoc.distanceSquared(target);
        if (distSq > teleportDistance * teleportDistance) {
            // Far away: skip straight to the target point, then keep replaying.
            FlyingFollowMovement.smoothTeleport(entity, target.clone());
            path.pollFirst();
            return;
        }

        if (distSq <= REACH_DISTANCE * REACH_DISTANCE) {
            // Reached this replay point — move on to the next one.
            path.pollFirst();
            target = path.peekFirst();
            if (target == null) {
                return;
            }
        }

        Location next = petLoc.clone().add(target.toVector().subtract(petLoc.toVector())
                .normalize().multiply(ADVANCE_SPEED));
        next.setYaw(ownerLoc.getYaw());
        FlyingFollowMovement.smoothTeleport(entity, next);
    }

    @Override
    public void remove(ActivePet activePet, Entity entity) {
        if (activePet != null) {
            echoes.remove(activePet.getPetId());
        }
    }
}

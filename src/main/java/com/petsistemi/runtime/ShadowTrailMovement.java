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
 * Shadow trail movement: the pet glides along the owner's footsteps like a ground
 * shadow — the trail queue is identical to {@link TrailMovement}, but every target
 * is projected onto the world surface (highest block), so the pet hugs the ground.
 */
public class ShadowTrailMovement implements PetMovementController {

    private static final double TRAIL_SPACING = 0.5;
    private static final double DEFAULT_TRAIL_LENGTH = 6.0;
    private static final double DEFAULT_TELEPORT_DISTANCE = 30.0;
    private static final double GROUND_OFFSET = 0.05;

    private final Map<UUID, Deque<Location>> trails = new HashMap<>();

    @Override
    public void initialize(ActivePet activePet, Entity entity, Player owner) {
        trails.put(activePet.getPetId(), new ArrayDeque<>());
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
        Deque<Location> trail = trails.computeIfAbsent(petId, k -> new ArrayDeque<>());

        Location ownerLoc = owner.getLocation();
        Location petLoc = entity.getLocation();

        if (!petLoc.getWorld().equals(ownerLoc.getWorld())) {
            trail.clear();
            FlyingFollowMovement.smoothTeleport(entity, project(ownerLoc));
            return;
        }

        TrailMovement.pushOwnerPosition(trail, ownerLoc, TRAIL_SPACING, trailLength);

        Location target = trail.peekFirst();
        if (target == null) {
            return;
        }
        Location groundTarget = project(target);
        if (petLoc.distanceSquared(groundTarget) > teleportDistance * teleportDistance) {
            FlyingFollowMovement.smoothTeleport(entity, groundTarget);
            return;
        }

        Location next = petLoc.clone().add(groundTarget.toVector().subtract(petLoc.toVector()).multiply(0.25));
        next.setYaw(ownerLoc.getYaw());
        FlyingFollowMovement.smoothTeleport(entity, next);
    }

    /** Projects a location onto the highest solid block of its world column. */
    static Location project(Location location) {
        if (location.getWorld() == null) {
            return location.clone();
        }
        double y = location.getWorld().getHighestBlockYAt(location.getBlockX(), location.getBlockZ());
        Location projected = location.clone();
        projected.setY(y + GROUND_OFFSET);
        return projected;
    }

    @Override
    public void remove(ActivePet activePet, Entity entity) {
        if (activePet != null) {
            trails.remove(activePet.getPetId());
        }
    }
}

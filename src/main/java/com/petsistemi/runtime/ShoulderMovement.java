package com.petsistemi.runtime;

import com.petsistemi.domain.PetFollowMode;
import com.petsistemi.domain.PetMovementDefinition;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * Shoulder movement: the pet rides near the owner's shoulder — a short distance
 * forward and up, smoothly following every step.
 */
public class ShoulderMovement implements PetMovementController {

    private static final double DEFAULT_HEIGHT = 0.9;
    private static final double DEFAULT_FORWARD = 0.35;
    private static final double DEFAULT_TELEPORT_DISTANCE = 24.0;

    @Override
    public void initialize(ActivePet activePet, Entity entity, Player owner) {
        // no-op
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
        double height = mov != null && mov.height() > 0.0 ? mov.height() : DEFAULT_HEIGHT;
        double forward = mov != null && mov.followDistance() > 0.0 ? mov.followDistance() : DEFAULT_FORWARD;
        double teleportDistance = mov != null && mov.teleportDistance() > 0.0 ? mov.teleportDistance() : DEFAULT_TELEPORT_DISTANCE;

        Location ownerLoc = owner.getLocation();
        Location petLoc = entity.getLocation();

        if (!petLoc.getWorld().equals(ownerLoc.getWorld())) {
            FlyingFollowMovement.smoothTeleport(entity, targetPosition(ownerLoc, forward, height));
            return;
        }

        Location target = targetPosition(ownerLoc, forward, height);
        if (target.distanceSquared(petLoc) > teleportDistance * teleportDistance) {
            FlyingFollowMovement.smoothTeleport(entity, target);
            return;
        }

        // Tight follow (k=0.45 keeps the pet on the shoulder)
        Location next = petLoc.clone().add(target.toVector().subtract(petLoc.toVector()).multiply(0.45));
        next.setYaw(ownerLoc.getYaw());
        next.setPitch(ownerLoc.getPitch());
        FlyingFollowMovement.smoothTeleport(entity, next);
    }

    /** Pure math: shoulder spot — forward of the owner's facing direction, at the given height. */
    public static Location targetPosition(Location ownerLoc, double forward, double height) {
        double yaw = Math.toRadians(ownerLoc.getYaw());
        Vector dir = new Vector(-Math.sin(yaw), 0.0, Math.cos(yaw));
        return ownerLoc.clone().add(dir.multiply(forward)).add(0.0, height, 0.0);
    }

    @Override
    public void remove(ActivePet activePet, Entity entity) {
        // no-op
    }
}

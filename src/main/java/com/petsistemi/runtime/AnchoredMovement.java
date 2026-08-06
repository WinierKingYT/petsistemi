package com.petsistemi.runtime;

import com.petsistemi.domain.PetAnchorDefinition;
import com.petsistemi.domain.PetAnchorPosition;
import com.petsistemi.domain.PetFollowMode;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * Anchored movement: the pet holds a fixed, owner-relative position (behind-right,
 * left shoulder, above head, ...) without any pathfinding. Cheap and precise for
 * display entities; the position rotates with the owner's facing when configured.
 */
public class AnchoredMovement implements PetMovementController {

    private static final double DEFAULT_TELEPORT_DISTANCE = 24.0;
    private static final double FOLLOW_K = 0.4;

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

        PetAnchorDefinition anchor = activePet.getMovementDefinition() != null
                && activePet.getMovementDefinition().anchor() != null
                ? activePet.getMovementDefinition().anchor()
                : PetAnchorDefinition.DEFAULT;

        double teleportDistance = activePet.getMovementDefinition() != null
                && activePet.getMovementDefinition().teleportDistance() > 0.0
                ? activePet.getMovementDefinition().teleportDistance()
                : DEFAULT_TELEPORT_DISTANCE;

        Location ownerLoc = owner.getLocation();
        Location petLoc = entity.getLocation();

        if (!petLoc.getWorld().equals(ownerLoc.getWorld())) {
            FlyingFollowMovement.smoothTeleport(entity, anchorPosition(ownerLoc, anchor));
            return;
        }

        Location target = anchorPosition(ownerLoc, anchor);
        if (target.distanceSquared(petLoc) > teleportDistance * teleportDistance) {
            FlyingFollowMovement.smoothTeleport(entity, target);
            return;
        }

        Location next = petLoc.clone().add(target.toVector().subtract(petLoc.toVector()).multiply(FOLLOW_K));
        next.setYaw(ownerLoc.getYaw());
        next.setPitch(ownerLoc.getPitch());
        FlyingFollowMovement.smoothTeleport(entity, next);
    }

    /** Pure math: world position for an anchor slot relative to the owner. */
    public static Location anchorPosition(Location ownerLoc, PetAnchorDefinition anchor) {
        double yaw = anchor.rotateWithOwner() ? Math.toRadians(ownerLoc.getYaw()) : 0.0;
        Vector forward = new Vector(-Math.sin(yaw), 0.0, Math.cos(yaw));
        Vector right = new Vector(-Math.cos(yaw), 0.0, -Math.sin(yaw));

        Vector offset;
        switch (anchor.position()) {
            case BEHIND_RIGHT -> offset = right.multiply(anchor.distance() * 0.55).add(forward.multiply(-anchor.distance() * 0.85));
            case BEHIND_LEFT -> offset = right.multiply(-anchor.distance() * 0.55).add(forward.multiply(-anchor.distance() * 0.85));
            case FRONT -> offset = forward.multiply(anchor.distance());
            case RIGHT_SHOULDER -> offset = right.multiply(anchor.distance() * 0.35).add(forward.multiply(anchor.distance() * 0.15));
            case LEFT_SHOULDER -> offset = right.multiply(-anchor.distance() * 0.35).add(forward.multiply(anchor.distance() * 0.15));
            case WAIST -> offset = right.multiply(0.0).add(forward.multiply(anchor.distance() * 0.25));
            case BELOW -> offset = new Vector(0.0, -anchor.height(), 0.0);
            case ABOVE_HEAD -> offset = new Vector(0.0, 0.0, 0.0);
            default -> offset = new Vector(0.0, 0.0, 0.0);
        }
        return ownerLoc.clone().add(offset).add(0.0, anchor.height(), 0.0);
    }

    @Override
    public void remove(ActivePet activePet, Entity entity) {
        // no-op
    }
}

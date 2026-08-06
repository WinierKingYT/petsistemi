package com.petsistemi.runtime;

import com.petsistemi.domain.PetFollowMode;
import com.petsistemi.domain.PetMovementDefinition;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

/**
 * Teleport-only movement: the pet stays exactly where it was summoned and only
 * teleports to the owner when they get too far away (also used for STATIC_NEAR_OWNER).
 * Honors STAY/WANDER follow modes by holding position entirely.
 */
public class TeleportOnlyMovement implements PetMovementController {

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
        double teleportDistance = mov != null && mov.teleportDistance() > 0.0 ? mov.teleportDistance() : DEFAULT_TELEPORT_DISTANCE;

        Location ownerLoc = owner.getLocation();
        Location petLoc = entity.getLocation();

        if (!petLoc.getWorld().equals(ownerLoc.getWorld())
                || petLoc.distanceSquared(ownerLoc) > teleportDistance * teleportDistance) {
            FlyingFollowMovement.smoothTeleport(entity, ownerLoc.clone().add(0.0, 1.0, 0.0));
        }
    }

    @Override
    public void remove(ActivePet activePet, Entity entity) {
        // no-op
    }
}

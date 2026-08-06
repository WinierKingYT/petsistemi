package com.petsistemi.runtime;

import com.petsistemi.domain.PetFollowMode;
import com.petsistemi.domain.PetMovementDefinition;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Hover movement: the pet floats above the owner with a gentle bobbing motion.
 * Tracks the owner's x/z smoothly; no pathfinding.
 */
public class HoverMovement implements PetMovementController {

    private static final double DEFAULT_HEIGHT = 2.2;
    private static final double BOB_AMPLITUDE = 0.12;
    private static final double BOB_SPEED = 0.15;
    private static final double DEFAULT_TELEPORT_DISTANCE = 24.0;

    private final Map<UUID, Double> phases = new HashMap<>();

    @Override
    public void initialize(ActivePet activePet, Entity entity, Player owner) {
        phases.put(activePet.getPetId(), Math.random() * 2.0 * Math.PI);
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
        double teleportDistance = mov != null && mov.teleportDistance() > 0.0 ? mov.teleportDistance() : DEFAULT_TELEPORT_DISTANCE;

        UUID petId = activePet.getPetId();
        double phase = phases.getOrDefault(petId, 0.0);
        phases.put(petId, phase + BOB_SPEED);

        Location ownerLoc = owner.getLocation();
        Location petLoc = entity.getLocation();

        if (!petLoc.getWorld().equals(ownerLoc.getWorld())) {
            FlyingFollowMovement.smoothTeleport(entity, targetPosition(ownerLoc, height, phase));
            return;
        }

        Location target = targetPosition(ownerLoc, height, phase);
        if (target.distanceSquared(petLoc) > teleportDistance * teleportDistance) {
            FlyingFollowMovement.smoothTeleport(entity, target);
            return;
        }

        // Gentle lerp toward the hover point
        double k = 0.15;
        Location next = petLoc.clone().add(target.toVector().subtract(petLoc.toVector()).multiply(k));
        next.setYaw(ownerLoc.getYaw());
        FlyingFollowMovement.smoothTeleport(entity, next);
    }

    /** Pure math: hover point directly above the owner with a sinusoidal bob. */
    public static Location targetPosition(Location ownerLoc, double height, double phase) {
        return ownerLoc.clone().add(0.0, height + Math.sin(phase) * BOB_AMPLITUDE, 0.0);
    }

    @Override
    public void remove(ActivePet activePet, Entity entity) {
        if (activePet != null) {
            phases.remove(activePet.getPetId());
        }
    }
}

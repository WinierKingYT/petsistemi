package com.petsistemi.runtime;

import com.petsistemi.domain.PetFollowMode;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Organic bee/particle swarm movement where children entities orbit around the owner
 * in a dynamic, multi-layered cloud pattern.
 */
public class SwarmCloudMovement implements PetMovementController {

    private static final double ANGLE_STEP = 0.05;

    /**
     * Per-pet phase. One controller instance is shared by every swarm on the server, so a
     * single field would phase-lock all of them and advance the angle once per pet per
     * tick instead of once per tick.
     */
    private final Map<UUID, Double> angles = new HashMap<>();

    @Override
    public void initialize(ActivePet pet, Entity entity, Player owner) {
        if (pet != null) {
            angles.put(pet.getPetId(), 0.0);
        }
    }

    @Override
    public void tick(ActivePet activePet, Entity primaryEntity, Player owner) {
        if (owner == null || primaryEntity == null || activePet == null) {
            return;
        }
        // STAY/WANDER hold position, exactly like every other movement type. swarm_bees
        // advertises `allowed-modes: [FOLLOW, STAY]`, so STAY has to actually stop it.
        if (activePet.getFollowMode() != PetFollowMode.FOLLOW) {
            return;
        }

        Location ownerLoc = owner.getLocation();
        double angleState = angles.getOrDefault(activePet.getPetId(), 0.0) + ANGLE_STEP;
        if (angleState > Math.PI * 2) {
            angleState = 0.0;
        }
        angles.put(activePet.getPetId(), angleState);

        // Primary entity follows floating above owner
        double headY = ownerLoc.getY() + 2.2 + Math.sin(angleState) * 0.2;
        Location primaryTarget = new Location(
                ownerLoc.getWorld(),
                ownerLoc.getX(),
                headY,
                ownerLoc.getZ(),
                ownerLoc.getYaw(),
                ownerLoc.getPitch()
        );
        primaryEntity.teleport(primaryTarget);

        // Move children in dynamic swarm cloud around primary entity
        List<Entity> children = activePet.getChildren();
        if (children == null || children.isEmpty()) {
            return;
        }

        double radius = 1.2;
        int count = children.size();
        for (int i = 0; i < count; i++) {
            Entity child = children.get(i);
            if (child == null || !child.isValid()) continue;

            double phase = angleState + (i * Math.PI * 2 / count);
            double xOffset = Math.cos(phase) * radius;
            double zOffset = Math.sin(phase) * radius;
            double yOffset = Math.sin(phase * 2.0) * 0.4;

            Location childTarget = new Location(
                    ownerLoc.getWorld(),
                    primaryTarget.getX() + xOffset,
                    primaryTarget.getY() + yOffset,
                    primaryTarget.getZ() + zOffset,
                    (float) (phase * 180.0 / Math.PI),
                    0.0f
            );
            child.teleport(childTarget);
        }
    }

    @Override
    public void remove(ActivePet pet, Entity entity) {
        if (pet != null) {
            angles.remove(pet.getPetId());
        }
    }
}

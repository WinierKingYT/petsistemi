package com.petsistemi.runtime;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Organic bee/particle swarm movement where children entities orbit around the owner
 * in a dynamic, multi-layered cloud pattern.
 */
public class SwarmCloudMovement implements PetMovementController {

    private double angleState = 0.0;

    @Override
    public void initialize(ActivePet pet, Entity entity, Player owner) {
    }

    @Override
    public void tick(ActivePet activePet, Entity primaryEntity, Player owner) {
        if (owner == null || primaryEntity == null || activePet == null) {
            return;
        }

        Location ownerLoc = owner.getLocation();
        angleState += 0.05;
        if (angleState > Math.PI * 2) {
            angleState = 0.0;
        }

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
    }
}

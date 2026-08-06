package com.petsistemi.runtime;

import com.petsistemi.domain.PetFollowMode;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * Formation movement: positions the primary pet at slot 0 (beside the owner) and
 * any tracked child entities (MULTI_ENTITY) around the owner in a rotating formation
 * that turns with the owner's facing.
 */
public class FormationMovement implements PetMovementController {

    private static final double PRIMARY_SIDE = 0.9;
    private static final double PRIMARY_HEIGHT = 0.4;
    private static final double CHILD_RADIUS = 1.3;
    private static final double CHILD_HEIGHT = 0.6;
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

        double teleportDistance = activePet.getMovementDefinition() != null
                && activePet.getMovementDefinition().teleportDistance() > 0.0
                ? activePet.getMovementDefinition().teleportDistance()
                : DEFAULT_TELEPORT_DISTANCE;

        Location ownerLoc = owner.getLocation();
        java.util.List<Entity> children = activePet.getChildren();

        // Slot 0: primary beside the owner
        Location primaryTarget = slotPosition(ownerLoc, 0, children.size() + 1, PRIMARY_SIDE, PRIMARY_HEIGHT);
        moveToward(entity, ownerLoc, primaryTarget, teleportDistance);

        // Slots 1..N: children in a circle around the owner
        int childCount = children.size();
        for (int i = 0; i < childCount; i++) {
            Entity child = children.get(i);
            if (child == null || !child.isValid()) {
                continue;
            }
            Location childTarget = slotPosition(ownerLoc, i + 1, childCount + 1, CHILD_RADIUS, CHILD_HEIGHT);
            moveToward(child, ownerLoc, childTarget, teleportDistance);
        }
    }

    private static void moveToward(Entity entity, Location ownerLoc, Location target, double teleportDistance) {
        Location current = entity.getLocation();
        if (current.getWorld() == null || !current.getWorld().equals(target.getWorld())
                || target.distanceSquared(current) > teleportDistance * teleportDistance) {
            FlyingFollowMovement.smoothTeleport(entity, target);
            return;
        }
        Location next = current.clone().add(target.toVector().subtract(current.toVector()).multiply(0.3));
        next.setYaw(ownerLoc.getYaw());
        FlyingFollowMovement.smoothTeleport(entity, next);
    }

    /**
     * Pure math: slot position relative to the owner. Slot 0 sits on the owner's right;
     * slots 1..N spread evenly around the owner in a circle facing with the owner.
     */
    public static Location slotPosition(Location ownerLoc, int slot, int totalSlots, double radius, double height) {
        double yaw = Math.toRadians(ownerLoc.getYaw());
        if (slot == 0) {
            Vector right = new Vector(-Math.cos(yaw), 0.0, -Math.sin(yaw));
            return ownerLoc.clone().add(right.multiply(radius)).add(0.0, height, 0.0);
        }
        double angle = 2.0 * Math.PI * (slot - 1) / Math.max(1, totalSlots - 1) + yaw;
        return ownerLoc.clone()
                .add(Math.cos(angle) * radius, height, Math.sin(angle) * radius);
    }

    @Override
    public void remove(ActivePet activePet, Entity entity) {
        // no-op
    }
}

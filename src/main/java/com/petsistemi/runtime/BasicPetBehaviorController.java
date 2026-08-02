package com.petsistemi.runtime;

import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;

public class BasicPetBehaviorController implements PetBehaviorController {

    private static final double TELEPORT_DISTANCE_SQUARED = 20.0 * 20.0;
    private static final double STOP_DISTANCE_SQUARED = 2.0 * 2.0;
    private static final double START_FOLLOW_DISTANCE_SQUARED = 3.5 * 3.5;

    private Location lastTargetLocation;

    @Override
    public void initialize(ActivePet activePet, LivingEntity entity, Player owner) {
        if (entity instanceof Mob mob) {
            mob.setTarget(null);
            mob.setAware(true);
        }
    }

    @Override
    public void tick(ActivePet activePet, LivingEntity entity, Player owner) {
        if (entity == null || !entity.isValid() || owner == null || !owner.isOnline()) {
            return;
        }

        Location petLoc = entity.getLocation();
        Location ownerLoc = owner.getLocation();

        // 1. World Change Check
        if (!petLoc.getWorld().equals(ownerLoc.getWorld())) {
            entity.teleport(SafePetLocationFinder.findSafeLocation(ownerLoc));
            return;
        }

        double distanceSquared = petLoc.distanceSquared(ownerLoc);

        // 2. Far away -> Teleport safely
        if (distanceSquared > TELEPORT_DISTANCE_SQUARED) {
            entity.teleport(SafePetLocationFinder.findSafeLocation(ownerLoc));
            lastTargetLocation = null;
            return;
        }

        // 3. Follow / Stop using Pathfinder
        if (entity instanceof Mob mob) {
            if (mob.getTarget() != null) {
                mob.setTarget(null);
            }

            if (distanceSquared < STOP_DISTANCE_SQUARED) {
                if (mob.getPathfinder().hasPath()) {
                    mob.getPathfinder().stopPathfinding();
                }
                lastTargetLocation = null;
            } else if (distanceSquared > START_FOLLOW_DISTANCE_SQUARED) {
                // Re-calculate path only if owner moved more than 1.5 blocks from last target location
                if (lastTargetLocation == null || lastTargetLocation.distanceSquared(ownerLoc) > 2.25) {
                    mob.getPathfinder().moveTo(ownerLoc, 1.3);
                    lastTargetLocation = ownerLoc.clone();
                }
            }
        }
    }

    @Override
    public void remove(ActivePet activePet, LivingEntity entity) {
        if (entity instanceof Mob mob) {
            mob.getPathfinder().stopPathfinding();
            mob.setTarget(null);
        }
        lastTargetLocation = null;
    }
}

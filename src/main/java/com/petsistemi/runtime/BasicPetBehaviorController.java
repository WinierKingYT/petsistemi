package com.petsistemi.runtime;

import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;

public class BasicPetBehaviorController implements PetBehaviorController {

    private static final double TELEPORT_DISTANCE_SQUARED = 20.0 * 20.0;
    private static final double STOP_DISTANCE_SQUARED = 3.0 * 3.0;
    private static final double START_FOLLOW_DISTANCE_SQUARED = 4.5 * 4.5;

    @Override
    public void initialize(ActivePet activePet, LivingEntity entity, Player owner) {
        if (entity instanceof Mob mob) {
            mob.setTarget(null);
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
            entity.teleport(ownerLoc);
            return;
        }

        double distanceSquared = petLoc.distanceSquared(ownerLoc);

        // 2. Far away -> Teleport
        if (distanceSquared > TELEPORT_DISTANCE_SQUARED) {
            entity.teleport(ownerLoc);
            return;
        }

        // 3. Follow / Stop using Pathfinder
        if (entity instanceof Mob mob) {
            // Prevent pet from targeting other players or entities
            if (mob.getTarget() != null) {
                mob.setTarget(null);
            }

            if (distanceSquared < STOP_DISTANCE_SQUARED) {
                mob.getPathfinder().stopPathfinding();
            } else if (distanceSquared > START_FOLLOW_DISTANCE_SQUARED) {
                // Move towards owner with speed multiplier
                mob.getPathfinder().moveTo(ownerLoc, 1.3);
            }
        }
    }

    @Override
    public void remove(ActivePet activePet, LivingEntity entity) {
        if (entity instanceof Mob mob) {
            mob.getPathfinder().stopPathfinding();
        }
    }
}

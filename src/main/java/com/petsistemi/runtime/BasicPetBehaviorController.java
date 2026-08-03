package com.petsistemi.runtime;

import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class BasicPetBehaviorController implements PetBehaviorController {

    private static final double TELEPORT_DISTANCE_SQUARED = 20.0 * 20.0;
    private static final double STOP_DISTANCE_SQUARED = 2.0 * 2.0;
    private static final double START_FOLLOW_DISTANCE_SQUARED = 3.5 * 3.5;

    private final Map<UUID, Location> lastTargets = new HashMap<>();

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

        UUID petId = activePet.getPetId();
        Location petLoc = entity.getLocation();
        Location ownerLoc = owner.getLocation();

        // 1. World Change Check
        if (!petLoc.getWorld().equals(ownerLoc.getWorld())) {
            entity.teleport(SafePetLocationFinder.findSafeLocation(ownerLoc));
            lastTargets.remove(petId);
            return;
        }

        double distanceSquared = petLoc.distanceSquared(ownerLoc);

        // 2. Far away -> Teleport safely
        if (distanceSquared > TELEPORT_DISTANCE_SQUARED) {
            entity.teleport(SafePetLocationFinder.findSafeLocation(ownerLoc));
            lastTargets.remove(petId);
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
                Location lookLoc = petLoc.clone();
                Vector dir = ownerLoc.toVector().subtract(petLoc.toVector());
                if (dir.lengthSquared() > 0.01) {
                    lookLoc.setDirection(dir);
                    entity.setRotation(lookLoc.getYaw(), lookLoc.getPitch());
                }
                lastTargets.remove(petId);
            } else if (distanceSquared > START_FOLLOW_DISTANCE_SQUARED) {
                Location lastTarget = lastTargets.get(petId);
                boolean hasNoPath = !mob.getPathfinder().hasPath();
                boolean targetMoved = lastTarget == null || !lastTarget.getWorld().equals(ownerLoc.getWorld()) || lastTarget.distanceSquared(ownerLoc) > 2.25;

                // Re-calculate path if path is missing or owner moved significantly
                if (hasNoPath || targetMoved) {
                    mob.getPathfinder().moveTo(ownerLoc, 1.3);
                    lastTargets.put(petId, ownerLoc.clone());
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
        if (activePet != null) {
            lastTargets.remove(activePet.getPetId());
        }
    }
}

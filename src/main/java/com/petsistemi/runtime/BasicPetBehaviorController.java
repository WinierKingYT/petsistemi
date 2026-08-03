package com.petsistemi.runtime;

import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class BasicPetBehaviorController implements PetBehaviorController {

    private final double teleportDistanceSquared;
    private final double stopDistanceSquared;
    private final double startFollowDistanceSquared;
    private final double followSpeed;

    private final Map<UUID, Location> lastTargets = new HashMap<>();

    /** Production constructor — reads distances and speed from plugin config. */
    public BasicPetBehaviorController(FileConfiguration config) {
        double teleport = config.getDouble("runtime.teleport-distance", 20.0);
        double stop     = config.getDouble("runtime.stop-distance",     2.0);
        double start    = config.getDouble("runtime.start-distance",    3.5);
        this.followSpeed               = config.getDouble("runtime.follow-speed", 1.2);
        this.teleportDistanceSquared   = teleport * teleport;
        this.stopDistanceSquared       = stop * stop;
        this.startFollowDistanceSquared = start * start;
    }

    /** Test / headless constructor with explicit values. */
    public BasicPetBehaviorController(double teleportDist, double stopDist, double startFollowDist, double followSpeed) {
        this.teleportDistanceSquared    = teleportDist * teleportDist;
        this.stopDistanceSquared        = stopDist * stopDist;
        this.startFollowDistanceSquared = startFollowDist * startFollowDist;
        this.followSpeed                = followSpeed;
    }

    /** No-arg constructor with safe defaults (used if config unavailable). */
    public BasicPetBehaviorController() {
        this(20.0, 2.0, 3.5, 1.2);
    }

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
        Location petLoc  = entity.getLocation();
        Location ownerLoc = owner.getLocation();

        // 1. World Change → teleport to owner
        if (!petLoc.getWorld().equals(ownerLoc.getWorld())) {
            entity.teleport(SafePetLocationFinder.findSafeLocation(ownerLoc));
            lastTargets.remove(petId);
            return;
        }

        double distSq = petLoc.distanceSquared(ownerLoc);

        // 2. Too far → teleport safely behind owner
        if (distSq > teleportDistanceSquared) {
            entity.teleport(SafePetLocationFinder.findSafeLocation(ownerLoc));
            lastTargets.remove(petId);
            return;
        }

        // 3. Pathfinder follow / stop
        if (entity instanceof Mob mob) {
            if (mob.getTarget() != null) mob.setTarget(null);

            if (distSq < stopDistanceSquared) {
                // Stop and face owner
                if (mob.getPathfinder().hasPath()) mob.getPathfinder().stopPathfinding();
                Vector dir = ownerLoc.toVector().subtract(petLoc.toVector());
                if (dir.lengthSquared() > 0.01) {
                    Location look = petLoc.clone().setDirection(dir);
                    entity.setRotation(look.getYaw(), 0.0f); // keep pitch at 0 (natural look)
                }
                lastTargets.remove(petId);

            } else if (distSq > startFollowDistanceSquared) {
                Location lastTarget = lastTargets.get(petId);
                boolean hasNoPath    = !mob.getPathfinder().hasPath();
                boolean targetMoved  = lastTarget == null
                        || !lastTarget.getWorld().equals(ownerLoc.getWorld())
                        || lastTarget.distanceSquared(ownerLoc) > 2.25;

                if (hasNoPath || targetMoved) {
                    mob.getPathfinder().moveTo(ownerLoc, followSpeed);
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
        if (activePet != null) lastTargets.remove(activePet.getPetId());
    }
}

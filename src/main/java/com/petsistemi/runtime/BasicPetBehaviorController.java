package com.petsistemi.runtime;

import com.petsistemi.config.PluginConfiguration;
import com.petsistemi.config.RuntimeConfigurationSnapshot;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

public class BasicPetBehaviorController implements PetBehaviorController {

    private final AtomicReference<RuntimeConfigurationSnapshot> configSnapshot;

    private final double defaultTeleportDistanceSquared;
    private final double defaultStopDistanceSquared;
    private final double defaultStartFollowDistanceSquared;
    private final double defaultFollowSpeed;

    private final Map<UUID, Location> lastTargets = new HashMap<>();

    /** Production constructor using atomic configuration snapshot. */
    public BasicPetBehaviorController(AtomicReference<RuntimeConfigurationSnapshot> configSnapshot) {
        this.configSnapshot = configSnapshot;
        this.defaultTeleportDistanceSquared = 400.0;
        this.defaultStopDistanceSquared = 4.0;
        this.defaultStartFollowDistanceSquared = 12.25;
        this.defaultFollowSpeed = 1.2;
    }

    /** Legacy constructor — reads initial distances from Bukkit config. */
    public BasicPetBehaviorController(FileConfiguration config) {
        this((AtomicReference<RuntimeConfigurationSnapshot>) null);
    }

    /** Headless / test constructor with explicit values. */
    public BasicPetBehaviorController(double teleportDist, double stopDist, double startFollowDist, double followSpeed) {
        this.configSnapshot = null;
        this.defaultTeleportDistanceSquared = teleportDist * teleportDist;
        this.defaultStopDistanceSquared = stopDist * stopDist;
        this.defaultStartFollowDistanceSquared = startFollowDist * startFollowDist;
        this.defaultFollowSpeed = followSpeed;
    }

    /** No-arg constructor with safe defaults. */
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

        PluginConfiguration.RuntimeConfiguration runtimeConfig = (configSnapshot != null && configSnapshot.get() != null)
                ? configSnapshot.get().configuration().runtime()
                : null;

        double teleportDistSq = runtimeConfig != null ? runtimeConfig.teleportDistance() * runtimeConfig.teleportDistance() : defaultTeleportDistanceSquared;
        double stopDistSq     = runtimeConfig != null ? runtimeConfig.stopDistance() * runtimeConfig.stopDistance() : defaultStopDistanceSquared;
        double startDistSq    = runtimeConfig != null ? runtimeConfig.startDistance() * runtimeConfig.startDistance() : defaultStartFollowDistanceSquared;
        double speed          = runtimeConfig != null ? runtimeConfig.followSpeed() : defaultFollowSpeed;

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
        if (distSq > teleportDistSq) {
            entity.teleport(SafePetLocationFinder.findSafeLocation(ownerLoc));
            lastTargets.remove(petId);
            return;
        }

        // 3. Pathfinder follow / stop
        if (entity instanceof Mob mob) {
            if (mob.getTarget() != null) mob.setTarget(null);

            if (distSq < stopDistSq) {
                // Stop and face owner
                if (mob.getPathfinder().hasPath()) mob.getPathfinder().stopPathfinding();
                Vector dir = ownerLoc.toVector().subtract(petLoc.toVector());
                if (dir.lengthSquared() > 0.01) {
                    Location look = petLoc.clone().setDirection(dir);
                    entity.setRotation(look.getYaw(), 0.0f);
                }
                lastTargets.remove(petId);

            } else if (distSq > startDistSq) {
                Location lastTarget = lastTargets.get(petId);
                boolean hasNoPath    = !mob.getPathfinder().hasPath();
                boolean targetMoved  = lastTarget == null
                        || !lastTarget.getWorld().equals(ownerLoc.getWorld())
                        || lastTarget.distanceSquared(ownerLoc) > 2.25;

                if (hasNoPath || targetMoved) {
                    mob.getPathfinder().moveTo(ownerLoc, speed);
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

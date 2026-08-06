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
    private final Map<UUID, Location> wanderAnchors = new HashMap<>();
    private final Map<UUID, Integer> wanderTicks = new HashMap<>();

    private static final double WANDER_RADIUS_SQUARED = 4.0 * 4.0;
    private static final int WANDER_REPICK_INTERVAL = 40;

    /** Production constructor using atomic configuration snapshot. */
    public BasicPetBehaviorController(AtomicReference<RuntimeConfigurationSnapshot> configSnapshot) {
        this.configSnapshot = configSnapshot;
        this.defaultTeleportDistanceSquared = 400.0;
        this.defaultStopDistanceSquared = 4.0;
        this.defaultStartFollowDistanceSquared = 12.25;
        this.defaultFollowSpeed = 1.2;
    }

    /** Legacy / test constructor — reads initial distances from Bukkit config. */
    public BasicPetBehaviorController(FileConfiguration config) {
        this.configSnapshot = null;
        if (config != null) {
            double teleport = config.getDouble("runtime.teleport-distance", 20.0);
            double stop     = config.getDouble("runtime.stop-distance",     2.0);
            double start    = config.getDouble("runtime.start-distance",    3.5);
            this.defaultFollowSpeed                = config.getDouble("runtime.follow-speed", 1.2);
            this.defaultTeleportDistanceSquared    = teleport * teleport;
            this.defaultStopDistanceSquared        = stop * stop;
            this.defaultStartFollowDistanceSquared = start * start;
        } else {
            this.defaultTeleportDistanceSquared    = 400.0;
            this.defaultStopDistanceSquared        = 4.0;
            this.defaultStartFollowDistanceSquared = 12.25;
            this.defaultFollowSpeed                = 1.2;
        }
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

        RuntimeConfigurationSnapshot snapshot = (configSnapshot != null) ? configSnapshot.get() : null;
        PluginConfiguration.RuntimeConfiguration runtimeConfig = (snapshot != null && snapshot.configuration() != null)
                ? snapshot.configuration().runtime()
                : null;

        double teleportDistSq = (runtimeConfig != null) ? runtimeConfig.teleportDistance() * runtimeConfig.teleportDistance() : defaultTeleportDistanceSquared;
        double stopDistSq     = (runtimeConfig != null) ? runtimeConfig.stopDistance() * runtimeConfig.stopDistance() : defaultStopDistanceSquared;
        double startDistSq    = (runtimeConfig != null) ? runtimeConfig.startDistance() * runtimeConfig.startDistance() : defaultStartFollowDistanceSquared;
        double speed          = (runtimeConfig != null) ? runtimeConfig.followSpeed() : defaultFollowSpeed;

        UUID petId = activePet.getPetId();
        Location petLoc  = entity.getLocation();
        Location ownerLoc = owner.getLocation();

        // 1. World Change → teleport to owner
        if (!petLoc.getWorld().equals(ownerLoc.getWorld())) {
            entity.teleport(SafePetLocationFinder.findSafeLocation(ownerLoc));
            lastTargets.remove(petId);
            return;
        }

        // 1b. Ridden → stay put (no pathfinding, no teleporting the rider)
        if (!entity.getPassengers().isEmpty()) {
            if (entity instanceof Mob mob && mob.getPathfinder().hasPath()) {
                mob.getPathfinder().stopPathfinding();
            }
            lastTargets.remove(petId);
            return;
        }

        // 1c. STAY → wait at current spot
        if (activePet.getFollowMode() == com.petsistemi.domain.PetFollowMode.STAY) {
            if (entity instanceof Mob mob && mob.getPathfinder().hasPath()) {
                mob.getPathfinder().stopPathfinding();
            }
            lastTargets.remove(petId);
            return;
        }

        // 1d. WANDER → roam around the anchor point
        if (activePet.getFollowMode() == com.petsistemi.domain.PetFollowMode.WANDER) {
            handleWander(activePet, entity, owner);
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

    private void handleWander(ActivePet activePet, LivingEntity entity, Player owner) {
        UUID petId = activePet.getPetId();
        Location petLoc = entity.getLocation();
        Location anchor = wanderAnchors.get(petId);
        if (anchor == null || !anchor.getWorld().equals(petLoc.getWorld())) {
            anchor = petLoc.clone();
            wanderAnchors.put(petId, anchor);
        }

        if (!(entity instanceof Mob mob)) {
            return;
        }
        if (mob.getTarget() != null) mob.setTarget(null);

        // Too far from anchor → walk back towards it
        if (petLoc.distanceSquared(anchor) > WANDER_RADIUS_SQUARED) {
            mob.getPathfinder().moveTo(anchor, 0.8);
            return;
        }

        int tickCounter = wanderTicks.merge(petId, 1, Integer::sum);
        if (tickCounter >= WANDER_REPICK_INTERVAL || !mob.getPathfinder().hasPath()) {
            wanderTicks.put(petId, 0);
            if (petLoc.getWorld() != null) {
                double angle = Math.random() * 2.0 * Math.PI;
                double radius = 1.0 + Math.random() * 3.0;
                Location target = petLoc.clone().add(Math.cos(angle) * radius, 0.0, Math.sin(angle) * radius);
                mob.getPathfinder().moveTo(target, 0.8);
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
            wanderAnchors.remove(activePet.getPetId());
            wanderTicks.remove(activePet.getPetId());
        }
    }
}

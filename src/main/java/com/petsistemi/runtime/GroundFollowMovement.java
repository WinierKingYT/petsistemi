package com.petsistemi.runtime;

import com.petsistemi.config.PluginConfiguration;
import com.petsistemi.config.RuntimeConfigurationSnapshot;
import com.petsistemi.domain.PetFollowMode;
import com.petsistemi.domain.PetMovementDefinition;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Classic ground-follow behavior for real mob pets (pathfinder based).
 * Ported from {@link BasicPetBehaviorController}; per-pet overrides come from
 * the definition's {@code movement} section (follow-distance, teleport-distance, follow-speed).
 */
public class GroundFollowMovement implements PetMovementController {

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

    public GroundFollowMovement(AtomicReference<RuntimeConfigurationSnapshot> configSnapshot) {
        this.configSnapshot = configSnapshot;
        this.defaultTeleportDistanceSquared = 400.0;
        this.defaultStopDistanceSquared = 4.0;
        this.defaultStartFollowDistanceSquared = 12.25;
        this.defaultFollowSpeed = 1.2;
    }

    /** Headless / test constructor with explicit values. */
    public GroundFollowMovement(double teleportDist, double stopDist, double startFollowDist, double followSpeed) {
        this.configSnapshot = null;
        this.defaultTeleportDistanceSquared = teleportDist * teleportDist;
        this.defaultStopDistanceSquared = stopDist * stopDist;
        this.defaultStartFollowDistanceSquared = startFollowDist * startFollowDist;
        this.defaultFollowSpeed = followSpeed;
    }

    /** No-arg constructor with safe defaults. */
    public GroundFollowMovement() {
        this(20.0, 2.0, 3.5, 1.2);
    }

    @Override
    public void initialize(ActivePet activePet, Entity entity, Player owner) {
        if (entity instanceof Mob mob) {
            mob.setTarget(null);
            mob.setAware(true);
        }
    }

    @Override
    public void tick(ActivePet activePet, Entity entity, Player owner) {
        if (entity == null || !entity.isValid() || owner == null || !owner.isOnline()) {
            return;
        }
        if (!(entity instanceof LivingEntity living)) {
            return;
        }

        double[] distances = distances(activePet);

        UUID petId = activePet.getPetId();
        Location petLoc  = living.getLocation();
        Location ownerLoc = owner.getLocation();

        // 1. World Change → teleport to owner
        if (!petLoc.getWorld().equals(ownerLoc.getWorld())) {
            living.teleport(SafePetLocationFinder.findSafeLocation(ownerLoc));
            lastTargets.remove(petId);
            return;
        }

        // 1b. Ridden → stay put (no pathfinding, no teleporting the rider)
        if (!living.getPassengers().isEmpty()) {
            if (living instanceof Mob mob && mob.getPathfinder().hasPath()) {
                mob.getPathfinder().stopPathfinding();
            }
            lastTargets.remove(petId);
            return;
        }

        // 1c. STAY → wait at current spot
        if (activePet.getFollowMode() == PetFollowMode.STAY) {
            if (living instanceof Mob mob && mob.getPathfinder().hasPath()) {
                mob.getPathfinder().stopPathfinding();
            }
            lastTargets.remove(petId);
            return;
        }

        // 1d. WANDER → roam around the anchor point
        if (activePet.getFollowMode() == PetFollowMode.WANDER) {
            handleWander(activePet, living, owner);
            return;
        }

        double distSq = petLoc.distanceSquared(ownerLoc);

        // 2. Too far → teleport safely behind owner
        if (distSq > distances[0]) {
            living.teleport(SafePetLocationFinder.findSafeLocation(ownerLoc));
            lastTargets.remove(petId);
            return;
        }

        // 3. Pathfinder follow / stop
        if (living instanceof Mob mob) {
            if (mob.getTarget() != null) mob.setTarget(null);

            if (distSq < distances[1]) {
                if (mob.getPathfinder().hasPath()) mob.getPathfinder().stopPathfinding();
                Vector dir = ownerLoc.toVector().subtract(petLoc.toVector());
                if (dir.lengthSquared() > 0.01) {
                    Location look = petLoc.clone().setDirection(dir);
                    living.setRotation(look.getYaw(), 0.0f);
                }
                lastTargets.remove(petId);

            } else if (distSq > distances[2]) {
                Location lastTarget = lastTargets.get(petId);
                boolean hasNoPath    = !mob.getPathfinder().hasPath();
                boolean targetMoved  = lastTarget == null
                        || !lastTarget.getWorld().equals(ownerLoc.getWorld())
                        || lastTarget.distanceSquared(ownerLoc) > 2.25;

                if (hasNoPath || targetMoved) {
                    mob.getPathfinder().moveTo(ownerLoc, distances[3]);
                    lastTargets.put(petId, ownerLoc.clone());
                }
            }

            // Water swimming buoyancy
            if (living.isInWater() && owner.isInWater()) {
                Vector v = living.getVelocity();
                if (v.getY() < 0.05) {
                    living.setVelocity(v.setY(0.08));
                }
            }
        }
    }

    /**
     * Resolves the effective follow geometry. Precedence: per-pet {@code movement}
     * overrides > {@code config.yml runtime.*} > constructor defaults.
     *
     * @return [teleportDistSq, stopDistSq, startDistSq, followSpeed]
     */
    double[] distances(ActivePet activePet) {
        double teleportDistSq = defaultTeleportDistanceSquared;
        double stopDistSq     = defaultStopDistanceSquared;
        double startDistSq    = defaultStartFollowDistanceSquared;
        double speed          = defaultFollowSpeed;

        RuntimeConfigurationSnapshot snapshot = (configSnapshot != null) ? configSnapshot.get() : null;
        PluginConfiguration.RuntimeConfiguration runtimeConfig = (snapshot != null && snapshot.configuration() != null)
                ? snapshot.configuration().runtime()
                : null;
        if (runtimeConfig != null) {
            teleportDistSq = runtimeConfig.teleportDistance() * runtimeConfig.teleportDistance();
            stopDistSq     = runtimeConfig.stopDistance() * runtimeConfig.stopDistance();
            startDistSq    = runtimeConfig.startDistance() * runtimeConfig.startDistance();
            speed          = runtimeConfig.followSpeed();
        }

        PetMovementDefinition mov = activePet != null ? activePet.getMovementDefinition() : null;
        if (mov != null) {
            if (mov.teleportDistance() > 0.0) {
                teleportDistSq = mov.teleportDistance() * mov.teleportDistance();
            }
            if (mov.followSpeed() > 0.0) {
                speed = mov.followSpeed();
            }
            if (mov.followDistance() > 0.0) {
                startDistSq = mov.followDistance() * mov.followDistance();
                stopDistSq  = Math.pow(Math.max(1.0, mov.followDistance() * 0.6), 2.0);
            }
        }

        return new double[]{teleportDistSq, stopDistSq, startDistSq, speed};
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
    public void remove(ActivePet activePet, Entity entity) {
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

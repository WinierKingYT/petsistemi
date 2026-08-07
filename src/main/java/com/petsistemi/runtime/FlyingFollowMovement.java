package com.petsistemi.runtime;

import com.petsistemi.config.PluginConfiguration;
import com.petsistemi.config.RuntimeConfigurationSnapshot;
import com.petsistemi.domain.PetFollowMode;
import com.petsistemi.domain.PetMovementDefinition;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Floating familiar movement: the pet interpolates toward a point beside the owner
 * (side offset + height). No pathfinding; used with display representations.
 */
public class FlyingFollowMovement implements PetMovementController {

    private static final double DEFAULT_HEIGHT = 1.5;
    private static final double DEFAULT_SIDE_OFFSET = 1.1;
    private static final double DEFAULT_FOLLOW_SPEED = 0.18;
    private static final double DEFAULT_TELEPORT_DISTANCE = 24.0;
    private static final int SMOOTH_TELEPORT_TICKS = 4;

    private final AtomicReference<RuntimeConfigurationSnapshot> configSnapshot;

    public FlyingFollowMovement(AtomicReference<RuntimeConfigurationSnapshot> configSnapshot) {
        this.configSnapshot = configSnapshot;
    }

    /** Headless / test constructor. */
    public FlyingFollowMovement() {
        this(null);
    }

    @Override
    public void initialize(ActivePet activePet, Entity entity, Player owner) {
        // no-op
    }

    @Override
    public void tick(ActivePet activePet, Entity entity, Player owner) {
        if (entity == null || !entity.isValid() || owner == null || !owner.isOnline()) {
            return;
        }

        PetMovementDefinition mov = activePet != null ? activePet.getMovementDefinition() : null;
        double height = mov != null && mov.height() > 0.0 ? mov.height() : DEFAULT_HEIGHT;
        double sideOffset = mov != null && mov.sideOffset() > 0.0 ? mov.sideOffset() : DEFAULT_SIDE_OFFSET;
        double followSpeed = mov != null && mov.followSpeed() > 0.0 ? mov.followSpeed() : DEFAULT_FOLLOW_SPEED;
        double teleportDistance = mov != null && mov.teleportDistance() > 0.0
                ? mov.teleportDistance()
                : defaultTeleportDistance();

        Location ownerLoc = owner.getLocation();
        Location petLoc = entity.getLocation();

        // World change → snap to owner
        if (!petLoc.getWorld().equals(ownerLoc.getWorld())) {
            smoothTeleport(entity, ownerLoc.clone().add(0.0, height, 0.0));
            return;
        }

        // STAY (and WANDER for flying pets) → hold current position
        if (activePet != null && activePet.getFollowMode() != PetFollowMode.FOLLOW) {
            return;
        }

        Location target = targetLocation(ownerLoc, sideOffset, height);

        Vector delta = target.toVector().subtract(petLoc.toVector());
        double distSq = delta.lengthSquared();
        if (distSq <= 0.0001) {
            return;
        }
        if (distSq > teleportDistance * teleportDistance) {
            smoothTeleport(entity, target);
            return;
        }

        // Fast tracking for Elytra gliding or vehicle riding
        if ((owner.isGliding() || owner.isInsideVehicle()) && distSq > 9.0) {
            smoothTeleport(entity, target);
            return;
        }

        // Exponential approach: followSpeed is the approach factor per update (at 20-tick cadence).
        double k = Math.min(1.0, followSpeed * 4.0);
        Location next = petLoc.clone().add(delta.multiply(k));
        next.setYaw(ownerLoc.getYaw());
        next.setPitch(ownerLoc.getPitch());
        smoothTeleport(entity, next);
    }

    /** Pure math: desired position beside the owner, offset to the owner's right side. */
    public static Location targetLocation(Location ownerLoc, double sideOffset, double height) {
        double yaw = Math.toRadians(ownerLoc.getYaw());
        // Right vector when facing getDirection(): (-cos(yaw), 0, -sin(yaw))
        Vector right = new Vector(-Math.cos(yaw), 0.0, -Math.sin(yaw));
        return ownerLoc.clone().add(right.multiply(sideOffset)).add(0.0, height, 0.0);
    }

    private double defaultTeleportDistance() {
        RuntimeConfigurationSnapshot snapshot = (configSnapshot != null) ? configSnapshot.get() : null;
        PluginConfiguration.RuntimeConfiguration runtimeConfig = (snapshot != null && snapshot.configuration() != null)
                ? snapshot.configuration().runtime()
                : null;
        if (runtimeConfig != null && runtimeConfig.teleportDistance() > 0.0) {
            return runtimeConfig.teleportDistance();
        }
        return DEFAULT_TELEPORT_DISTANCE;
    }

    static void smoothTeleport(Entity entity, Location target) {
        if (entity instanceof Display display) {
            display.setTeleportDuration(SMOOTH_TELEPORT_TICKS);
        }
        entity.teleport(target);
    }

    @Override
    public void remove(ActivePet activePet, Entity entity) {
        // no-op
    }
}

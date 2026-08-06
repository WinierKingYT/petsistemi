package com.petsistemi.runtime;

import com.petsistemi.config.RuntimeConfigurationSnapshot;
import com.petsistemi.domain.PetFollowMode;
import com.petsistemi.domain.PetMovementDefinition;
import com.petsistemi.domain.PetOrbitDefinition;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Orbit movement: the pet circles the owner on a fixed radius/height plane.
 * Pure position math; supports clockwise/counter-clockwise direction.
 */
public class OrbitMovement implements PetMovementController {

    private final AtomicReference<RuntimeConfigurationSnapshot> configSnapshot;
    private final Map<UUID, Double> angles = new HashMap<>();

    public OrbitMovement(AtomicReference<RuntimeConfigurationSnapshot> configSnapshot) {
        this.configSnapshot = configSnapshot;
    }

    /** Headless / test constructor. */
    public OrbitMovement() {
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
        PetOrbitDefinition orbit = mov != null && mov.orbit() != null ? mov.orbit() : PetOrbitDefinition.DEFAULT;

        UUID petId = activePet.getPetId();
        Location ownerLoc = owner.getLocation();
        Location petLoc = entity.getLocation();

        // World change → snap to owner and keep orbiting
        if (!petLoc.getWorld().equals(ownerLoc.getWorld())) {
            angles.remove(petId);
        }

        // STAY (and WANDER for orbit pets) → hold current position
        if (activePet.getFollowMode() != PetFollowMode.FOLLOW) {
            return;
        }

        double interval = activePet.getUpdateIntervalTicks() > 0 ? activePet.getUpdateIntervalTicks() : 5.0;
        double angle = nextAngle(angles.getOrDefault(petId, 0.0), orbit.angularSpeed(), interval, orbit.clockwise());
        angles.put(petId, angle);

        Location target = orbitPosition(ownerLoc, angle, orbit.radius(), orbit.height());
        FlyingFollowMovement.smoothTeleport(entity, target);
    }

    /** Pure math: advances an orbit angle (radians) by the configured speed. */
    public static double nextAngle(double current, double angularSpeed, double intervalTicks, boolean clockwise) {
        double delta = angularSpeed * (intervalTicks / 20.0);
        double next = current + (clockwise ? delta : -delta);
        return next % (2.0 * Math.PI);
    }

    /** Pure math: orbit position around a center. */
    public static Location orbitPosition(Location center, double angle, double radius, double height) {
        return center.clone()
                .add(Math.cos(angle) * radius, height, Math.sin(angle) * radius);
    }

    @Override
    public void remove(ActivePet activePet, Entity entity) {
        if (activePet != null) {
            angles.remove(activePet.getPetId());
        }
    }
}

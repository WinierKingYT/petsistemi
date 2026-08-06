package com.petsistemi.runtime;

import com.petsistemi.domain.PetFollowMode;
import com.petsistemi.domain.PetMovementDefinition;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Trail movement: the pet follows the owner's footsteps with a lag — the owner's
 * recent positions form a queue and the pet chases the oldest one, producing a
 * trailing companion (ideal for block/text display pets).
 */
public class TrailMovement implements PetMovementController {

    private static final double TRAIL_LENGTH_BLOCKS = 6.0;
    private static final double TRAIL_SPACING = 0.5;
    private static final double DEFAULT_TELEPORT_DISTANCE = 30.0;

    private final Map<UUID, Deque<Location>> trails = new HashMap<>();

    @Override
    public void initialize(ActivePet activePet, Entity entity, Player owner) {
        trails.put(activePet.getPetId(), new ArrayDeque<>());
    }

    @Override
    public void tick(ActivePet activePet, Entity entity, Player owner) {
        if (entity == null || !entity.isValid() || owner == null || !owner.isOnline()) {
            return;
        }

        if (activePet.getFollowMode() != PetFollowMode.FOLLOW) {
            return;
        }

        PetMovementDefinition mov = activePet.getMovementDefinition();
        double trailLength = mov != null && mov.followDistance() > 0.0 ? mov.followDistance() : TRAIL_LENGTH_BLOCKS;
        double teleportDistance = mov != null && mov.teleportDistance() > 0.0 ? mov.teleportDistance() : DEFAULT_TELEPORT_DISTANCE;

        UUID petId = activePet.getPetId();
        Deque<Location> trail = trails.computeIfAbsent(petId, k -> new ArrayDeque<>());

        Location ownerLoc = owner.getLocation();
        Location petLoc = entity.getLocation();

        if (!petLoc.getWorld().equals(ownerLoc.getWorld())) {
            trail.clear();
            FlyingFollowMovement.smoothTeleport(entity, ownerLoc.clone().add(0.0, 1.0, 0.0));
            return;
        }

        pushOwnerPosition(trail, ownerLoc, TRAIL_SPACING, trailLength);

        Location target = trail.peekFirst();
        if (target == null) {
            return;
        }
        if (target.distanceSquared(petLoc) > teleportDistance * teleportDistance) {
            FlyingFollowMovement.smoothTeleport(entity, target.clone().add(0.0, 0.0, 0.0));
            return;
        }

        Location next = petLoc.clone().add(target.toVector().subtract(petLoc.toVector()).multiply(0.25));
        next.setYaw(ownerLoc.getYaw());
        FlyingFollowMovement.smoothTeleport(entity, next);
    }

    /**
     * Records the owner's current position when far enough from the last recorded one,
     * trimming the queue to the configured trail length. The first element is where the
     * pet should be heading.
     */
    static void pushOwnerPosition(Deque<Location> trail, Location ownerLoc, double spacing, double trailLength) {
        Location last = trail.peekLast();
        boolean merge = last != null
                && last.getWorld() != null
                && ownerLoc.getWorld() != null
                && last.getWorld().equals(ownerLoc.getWorld())
                && last.distanceSquared(ownerLoc) < spacing * spacing;
        if (!merge) {
            trail.addLast(ownerLoc.clone());
        }
        int maxSize = Math.max(1, (int) Math.ceil(trailLength / spacing));
        while (trail.size() > maxSize) {
            trail.pollFirst();
        }
    }

    @Override
    public void remove(ActivePet activePet, Entity entity) {
        if (activePet != null) {
            trails.remove(activePet.getPetId());
        }
    }
}

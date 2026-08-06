package com.petsistemi.runtime;

import com.petsistemi.domain.PetFollowMode;
import com.petsistemi.domain.PetMovementDefinition;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Mirror movement: the pet stays at a fixed offset relative to the owner and
 * mirrors their pose (sneaking, gliding, jumping, yaw/pitch) with a configurable
 * tick delay. Uses a ring buffer of {@link OwnerPose} snapshots.
 */
public class MirrorMovement implements PetMovementController {

    private static final double DEFAULT_HEIGHT = 0.0;
    private static final double DEFAULT_SIDE_OFFSET = 1.2;
    private static final int DEFAULT_DELAY_TICKS = 10;
    private static final double JUMP_VELOCITY = 0.42;
    private static final double JUMP_THRESHOLD = 0.1;

    private final Map<UUID, Deque<OwnerPose>> poseHistories = new HashMap<>();
    private final Map<UUID, Boolean> lastOnGround = new HashMap<>();

    public record OwnerPose(float yaw, float pitch, boolean sneaking, boolean gliding, boolean jumping) {}

    @Override
    public void initialize(ActivePet activePet, Entity entity, Player owner) {
        poseHistories.put(activePet.getPetId(), new ArrayDeque<>());
        lastOnGround.put(activePet.getPetId(), owner.isOnGround());
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
        double height = mov != null && mov.height() > 0.0 ? mov.height() : DEFAULT_HEIGHT;
        double sideOffset = mov != null && mov.sideOffset() > 0.0 ? mov.sideOffset() : DEFAULT_SIDE_OFFSET;
        int delay = mov != null && mov.delayTicks() > 0 ? mov.delayTicks() : DEFAULT_DELAY_TICKS;

        UUID petId = activePet.getPetId();
        Deque<OwnerPose> history = poseHistories.computeIfAbsent(petId, k -> new ArrayDeque<>());
        Boolean wasOnGround = lastOnGround.getOrDefault(petId, true);

        Location ownerLoc = owner.getLocation();
        Location petLoc = entity.getLocation();

        if (!petLoc.getWorld().equals(ownerLoc.getWorld())) {
            history.clear();
            FlyingFollowMovement.smoothTeleport(entity, targetLocation(ownerLoc, sideOffset, height));
            return;
        }

        // Record current owner pose.
        boolean isJumping = !owner.isOnGround() && wasOnGround && owner.getVelocity().getY() > JUMP_THRESHOLD;
        history.addLast(new OwnerPose(ownerLoc.getYaw(), ownerLoc.getPitch(),
                owner.isSneaking(), owner.isGliding(), isJumping));
        lastOnGround.put(petId, owner.isOnGround());

        // Keep max buffer size equal to delay.
        while (history.size() > delay + 5) {
            history.pollFirst();
        }

        if (history.isEmpty()) {
            return;
        }

        // Position: stay beside the owner (like FlyingFollowMovement).
        Location pos = targetLocation(ownerLoc, sideOffset, height);

        // Pose: replay the oldest recorded pose if we have enough history.
        if (history.size() > delay) {
            OwnerPose past = history.pollFirst();
            pos.setYaw(past.yaw());
            pos.setPitch(past.pitch());

            if (entity instanceof LivingEntity living) {
                try {
                    living.setSneaking(past.sneaking());
                    living.setGliding(past.gliding());
                } catch (Throwable ignored) {}
                if (past.jumping()) {
                    living.setVelocity(living.getVelocity().setY(JUMP_VELOCITY));
                }
            }
        } else {
            pos.setYaw(ownerLoc.getYaw());
            pos.setPitch(ownerLoc.getPitch());
        }

        FlyingFollowMovement.smoothTeleport(entity, pos);
    }

    static Location targetLocation(Location ownerLoc, double sideOffset, double height) {
        double yaw = Math.toRadians(ownerLoc.getYaw());
        double dx = -Math.sin(yaw) * sideOffset;
        double dz = Math.cos(yaw) * sideOffset;
        return ownerLoc.clone().add(dx, height, dz);
    }

    @Override
    public void remove(ActivePet activePet, Entity entity) {
        if (activePet != null) {
            UUID pid = activePet.getPetId();
            poseHistories.remove(pid);
            lastOnGround.remove(pid);
        }
    }
}
package com.petsistemi.domain;

import org.bukkit.NamespacedKey;

/**
 * Movement definition of a pet. {@code null} fields mean "use runtime configuration defaults".
 */
public record PetMovementDefinition(
        PetMovementType type,
        NamespacedKey key,
        double followDistance,
        double teleportDistance,
        int updateIntervalTicks,
        double height,
        double sideOffset,
        double followSpeed,
        PetOrbitDefinition orbit,
        PetAnchorDefinition anchor,
        int delayTicks
) {

    public static final int DEFAULT_UPDATE_INTERVAL_TICKS = 5;
    public static final int MAX_DELAY_TICKS = 600;

    /** Backward-compatible constructor (pre-Milestone-2 fields only). */
    public PetMovementDefinition(
            PetMovementType type,
            double followDistance,
            double teleportDistance,
            int updateIntervalTicks,
            double height,
            double sideOffset,
            double followSpeed,
            PetOrbitDefinition orbit
    ) {
        this(type, RuntimeKeyResolver.movementKey(type), followDistance, teleportDistance, updateIntervalTicks, height, sideOffset,
                followSpeed, orbit, null, 0);
    }

    /** Backward-compatible constructor without delay (anchor included). */
    public PetMovementDefinition(
            PetMovementType type,
            double followDistance,
            double teleportDistance,
            int updateIntervalTicks,
            double height,
            double sideOffset,
            double followSpeed,
            PetOrbitDefinition orbit,
            PetAnchorDefinition anchor
    ) {
        this(type, RuntimeKeyResolver.movementKey(type), followDistance, teleportDistance, updateIntervalTicks, height, sideOffset,
                followSpeed, orbit, anchor, 0);
    }

    /** Backward-compatible constructor retaining the former canonical signature. */
    public PetMovementDefinition(PetMovementType type, double followDistance, double teleportDistance,
                                 int updateIntervalTicks, double height, double sideOffset, double followSpeed,
                                 PetOrbitDefinition orbit, PetAnchorDefinition anchor, int delayTicks) {
        this(type, RuntimeKeyResolver.movementKey(type), followDistance, teleportDistance, updateIntervalTicks, height,
                sideOffset, followSpeed, orbit, anchor, delayTicks);
    }

    public PetMovementDefinition {
        type = type != null ? type : PetMovementType.GROUND_FOLLOW;
        key = key != null ? key : RuntimeKeyResolver.movementKey(type);
        updateIntervalTicks = updateIntervalTicks < 0 ? 0 : updateIntervalTicks;
        delayTicks = delayTicks < 0 ? 0 : delayTicks;
        followDistance = sanitize(followDistance);
        teleportDistance = sanitize(teleportDistance);
        height = sanitize(height);
        sideOffset = sanitize(sideOffset);
        followSpeed = sanitize(followSpeed);
    }

    /** Extension-aware constructor; the enum remains a compatibility hint for legacy consumers. */
    public PetMovementDefinition(NamespacedKey key, double followDistance, double teleportDistance,
                                 int updateIntervalTicks, double height, double sideOffset, double followSpeed,
                                 PetOrbitDefinition orbit, PetAnchorDefinition anchor, int delayTicks) {
        this(RuntimeKeyResolver.builtInMovement(key) != null ? RuntimeKeyResolver.builtInMovement(key)
                        : PetMovementType.GROUND_FOLLOW,
                key, followDistance, teleportDistance, updateIntervalTicks, height, sideOffset, followSpeed,
                orbit, anchor, delayTicks);
    }

    private static double sanitize(double value) {
        return (Double.isFinite(value) && value > 0.0) ? value : 0.0;
    }

    public static PetMovementDefinition ground() {
        return new PetMovementDefinition(PetMovementType.GROUND_FOLLOW, 0.0, 0.0, 0, 0.0, 0.0, 0.0, null);
    }

    public static PetMovementDefinition flying(double teleportDistance, int updateIntervalTicks,
                                               double height, double sideOffset, double followSpeed) {
        return new PetMovementDefinition(PetMovementType.FLYING_FOLLOW, 0.0, teleportDistance,
                updateIntervalTicks, height, sideOffset, followSpeed, null);
    }

    public static PetMovementDefinition orbit(PetOrbitDefinition orbit, double teleportDistance, int updateIntervalTicks) {
        return new PetMovementDefinition(PetMovementType.ORBIT, 0.0, teleportDistance,
                updateIntervalTicks, 0.0, 0.0, 0.0, orbit != null ? orbit : PetOrbitDefinition.DEFAULT);
    }

    public static PetMovementDefinition anchored(PetAnchorDefinition anchor) {
        return new PetMovementDefinition(PetMovementType.ANCHORED, 0.0, 0.0, 0,
                0.0, 0.0, 0.0, null, anchor != null ? anchor : PetAnchorDefinition.DEFAULT);
    }

    public static PetMovementDefinition echo(double followDistance, double teleportDistance) {
        return new PetMovementDefinition(PetMovementType.ECHO, followDistance, teleportDistance,
                0, 0.0, 0.0, 0.0, null);
    }

    public static PetMovementDefinition shadowTrail(double followDistance, double teleportDistance) {
        return new PetMovementDefinition(PetMovementType.SHADOW_TRAIL, followDistance, teleportDistance,
                0, 0.0, 0.0, 0.0, null);
    }

    public static PetMovementDefinition roam(double radius, double teleportDistance, double speed) {
        return new PetMovementDefinition(PetMovementType.ROAM_NEAR_OWNER, radius, teleportDistance,
                0, 0.0, 0.0, speed, null);
    }

    public static PetMovementDefinition mirror(double height, double sideOffset, int delayTicks) {
        return new PetMovementDefinition(PetMovementType.MIRROR, 0.0, 0.0, 0,
                height, sideOffset, 0.0, null, null, delayTicks);
    }
}

package com.petsistemi.domain;

/**
 * Riding configuration for pets that allow players to mount them.
 */
public record PetMountDefinition(
        boolean enabled,
        String permission,
        double speedMultiplier,
        boolean allowFly
) {
    public static final PetMountDefinition DISABLED = new PetMountDefinition(false, null, 1.0, false);
}

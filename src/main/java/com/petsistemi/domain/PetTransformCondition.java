package com.petsistemi.domain;

/**
 * Environment/owner condition for a transform ({@code transforms[].when}).
 * Every field is optional; at least one must be set for the transform to be valid.
 * All conditions must match for the transform to apply.
 */
public record PetTransformCondition(
        PetOwnerState ownerState,
        String biome,
        String world,
        PetTimeOfDay timeOfDay,
        PetWeather weather,
        Integer minY,
        Integer maxY,
        Integer minLight,
        Integer maxLight
) {

    public PetTransformCondition(
            PetOwnerState ownerState,
            String biome,
            String world,
            PetTimeOfDay timeOfDay,
            PetWeather weather
    ) {
        this(ownerState, biome, world, timeOfDay, weather, null, null, null, null);
    }

    public boolean hasAny() {
        return ownerState != null || biome != null || world != null || timeOfDay != null || weather != null
                || minY != null || maxY != null || minLight != null || maxLight != null;
    }
}

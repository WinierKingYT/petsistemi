package com.petsistemi.domain;

/**
 * Per-pet reaction override ({@code reactions.<TYPE>}). {@code null} sound/particle
 * mean "keep the global default for that reaction"; {@code enabled: false} disables
 * the reaction for this pet entirely.
 */
public record PetReactionDefinition(
        boolean enabled,
        String sound,
        String particle,
        int particleCount,
        double volume
) {
}

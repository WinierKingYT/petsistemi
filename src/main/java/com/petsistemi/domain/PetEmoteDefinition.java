package com.petsistemi.domain;

/**
 * One entry of the per-pet {@code emotes:} section. An emote plays a sound and a
 * particle burst at the pet's location when triggered via {@code /pet emote <name>}.
 */
public record PetEmoteDefinition(
        boolean enabled,
        String sound,
        String particle,
        int particleCount,
        int cooldownSeconds
) {
}

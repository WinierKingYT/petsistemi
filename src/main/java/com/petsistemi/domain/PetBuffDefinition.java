package com.petsistemi.domain;

import org.bukkit.potion.PotionEffectType;

/**
 * Definition of a passive potion effect (buff) granted to the owner while the pet is spawned.
 */
public record PetBuffDefinition(
        PotionEffectType effectType,
        int amplifier,
        int minLevel,
        int durationTicks
) {
    public PetBuffDefinition {
        if (durationTicks <= 0) {
            durationTicks = 60; // 3 seconds default refresh window
        }
    }
}

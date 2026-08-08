package com.petsistemi.domain.animation;

import org.bukkit.NamespacedKey;

import java.util.Objects;

/**
 * A provider-neutral named animation clip. Providers may interpret blending when
 * they support it; vanilla/display adapters still receive and preserve the metadata.
 */
public record PetAnimationClipDefinition(
        NamespacedKey key,
        int priority,
        int blendInTicks,
        int blendOutTicks,
        boolean loop
) {
    public PetAnimationClipDefinition {
        Objects.requireNonNull(key, "animation clip key null olamaz.");
        priority = Math.max(0, priority);
        blendInTicks = Math.max(0, blendInTicks);
        blendOutTicks = Math.max(0, blendOutTicks);
    }
}

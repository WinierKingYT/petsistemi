package com.petsistemi.domain.visual;

import com.petsistemi.domain.animation.PetAnimationState;

import java.util.EnumMap;
import java.util.Map;

/** Item material, billboard mode and state-driven frames of a 2D sprite pet. */
public record PetSpriteDefinition(String material, PetSpriteBillboard billboard,
                                  Map<PetAnimationState, PetSpriteAnimationDefinition> animations) {
    public PetSpriteDefinition {
        material = material == null ? null : material.trim().toUpperCase(java.util.Locale.ROOT);
        billboard = billboard != null ? billboard : PetSpriteBillboard.CENTER;
        EnumMap<PetAnimationState, PetSpriteAnimationDefinition> copy = new EnumMap<>(PetAnimationState.class);
        if (animations != null) copy.putAll(animations);
        if (copy.isEmpty()) {
            throw new IllegalArgumentException("Sprite en az bir animation state içermelidir.");
        }
        animations = java.util.Collections.unmodifiableMap(copy);
    }

    public PetSpriteAnimationDefinition animation(PetAnimationState state) {
        PetSpriteAnimationDefinition selected = animations.get(state);
        if (selected == null) selected = animations.get(PetAnimationState.IDLE);
        if (selected == null) selected = animations.values().iterator().next();
        return selected;
    }
}

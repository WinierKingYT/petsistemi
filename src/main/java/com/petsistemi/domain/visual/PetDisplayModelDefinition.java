package com.petsistemi.domain.visual;

import com.petsistemi.domain.animation.PetAnimationState;

import java.util.EnumMap;
import java.util.Map;

/** Display-only skeleton and its provider-independent animation-state channels. */
public record PetDisplayModelDefinition(PetVisualGraphDefinition skeleton,
                                        Map<PetAnimationState, PetDisplayAnimationDefinition> animations) {
    public PetDisplayModelDefinition {
        if (skeleton == null) throw new IllegalArgumentException("Display model skeleton eksik.");
        EnumMap<PetAnimationState, PetDisplayAnimationDefinition> copy = new EnumMap<>(PetAnimationState.class);
        if (animations != null) copy.putAll(animations);
        animations = java.util.Collections.unmodifiableMap(copy);
    }
}

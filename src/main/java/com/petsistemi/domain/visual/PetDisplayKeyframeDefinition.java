package com.petsistemi.domain.visual;

/** One local bone pose at a clip-relative tick. */
public record PetDisplayKeyframeDefinition(int tick, PetVisualTransform transform) {
    public PetDisplayKeyframeDefinition {
        if (tick < 0) throw new IllegalArgumentException("Display model keyframe tick negatif olamaz.");
        transform = transform != null ? transform : PetVisualTransform.IDENTITY;
    }
}

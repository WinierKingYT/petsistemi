package com.petsistemi.runtime.mount;

/** Normalized rider input: sideways/forward are clamped to [-1, 1]. */
public record PetMountInput(float sideways, float forward, boolean jumping) {
    public static final PetMountInput NONE = new PetMountInput(0.0F, 0.0F, false);

    public PetMountInput {
        sideways = clamp(sideways);
        forward = clamp(forward);
    }

    private static float clamp(float value) {
        if (!Float.isFinite(value)) return 0.0F;
        return Math.max(-1.0F, Math.min(1.0F, value));
    }
}

package com.petsistemi.domain.animation;

/** Provider-independent runtime animation states shared by every representation. */
public enum PetAnimationState {
    IDLE("idle", 0, true),
    MOVING("walk", 10, true),
    SPRINTING("sprint", 20, true),
    SLEEPING("sleep", 30, true),
    ATTACKING("attack", 100, false);

    private final String defaultClip;
    private final int defaultPriority;
    private final boolean defaultLoop;

    PetAnimationState(String defaultClip, int defaultPriority, boolean defaultLoop) {
        this.defaultClip = defaultClip;
        this.defaultPriority = defaultPriority;
        this.defaultLoop = defaultLoop;
    }

    public String defaultClip() {
        return defaultClip;
    }

    public int defaultPriority() {
        return defaultPriority;
    }

    public boolean defaultLoop() {
        return defaultLoop;
    }
}

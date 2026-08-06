package com.petsistemi.domain;

/**
 * One runtime state entry from the per-pet {@code states:} section.
 *
 * <p>{@code afterTicks} is the idle delay in game ticks (20 tps). {@code 0} means
 * "use the global {@code features.idle-sleep.idle-seconds} value".</p>
 */
public record PetStateDefinition(int afterTicks, PetIdleAnimation animation) {

    public PetStateDefinition {
        afterTicks = Math.max(0, afterTicks);
        if (animation == null) {
            animation = PetIdleAnimation.NONE;
        }
    }
}

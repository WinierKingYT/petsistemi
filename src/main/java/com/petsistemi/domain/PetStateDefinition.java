package com.petsistemi.domain;

import com.petsistemi.domain.animation.PetAnimationClipDefinition;
import com.petsistemi.domain.animation.PetAnimationState;
import org.bukkit.NamespacedKey;

/**
 * One runtime state entry from the per-pet {@code states:} section.
 *
 * <p>{@code afterTicks} is the idle delay in game ticks (20 tps). {@code 0} means
 * "use the global {@code features.idle-sleep.idle-seconds} value".</p>
 */
public record PetStateDefinition(
        int afterTicks,
        PetIdleAnimation animation,
        NamespacedKey clip,
        int priority,
        int blendInTicks,
        int blendOutTicks,
        boolean loop
) {

    /** Legacy constructor retained for the original {@code animation: WALK/SLEEP} schema. */
    public PetStateDefinition(int afterTicks, PetIdleAnimation animation) {
        this(afterTicks, animation, null, -1, 2, 2, true);
    }

    public PetStateDefinition {
        afterTicks = Math.max(0, afterTicks);
        if (animation == null) {
            animation = PetIdleAnimation.NONE;
        }
        priority = Math.max(-1, priority);
        blendInTicks = Math.max(0, blendInTicks);
        blendOutTicks = Math.max(0, blendOutTicks);
    }

    /** Resolves new clip metadata while preserving legacy enum definitions. */
    public PetAnimationClipDefinition resolveClip(PetAnimationState state) {
        NamespacedKey resolved = clip != null ? clip : legacyClip(state, animation);
        int resolvedPriority = priority >= 0 ? priority : state.defaultPriority();
        return new PetAnimationClipDefinition(resolved, resolvedPriority, blendInTicks, blendOutTicks,
                clip == null && state == PetAnimationState.ATTACKING ? false : loop);
    }

    private static NamespacedKey legacyClip(PetAnimationState state, PetIdleAnimation animation) {
        String key = state.defaultClip();
        if (state == PetAnimationState.SLEEPING && animation != null && animation != PetIdleAnimation.NONE) {
            key = animation.name().toLowerCase(java.util.Locale.ROOT);
        } else if ((state == PetAnimationState.MOVING || state == PetAnimationState.SPRINTING)
                && animation == PetIdleAnimation.WALK) {
            key = state == PetAnimationState.SPRINTING ? "sprint" : "walk";
        }
        return new NamespacedKey(RuntimeKeyResolver.NAMESPACE, key);
    }
}

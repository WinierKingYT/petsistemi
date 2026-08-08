package com.petsistemi.domain;

/**
 * Per-pet runtime state machine from the {@code states:} YAML section.
 *
 * <p>Example:
 * <pre>
 * states:
 *   MOVING:
 *     animation: WALK
 *   IDLE:
 *     after-ticks: 100
 *     animation: SLEEP
 *   ATTACKING:
 *     clip: modelengine:bite
 *     priority: 100
 *     blend-in-ticks: 2
 *     blend-out-ticks: 3
 * </pre>
 * When {@code idle} is defined, the pet's idle/sleep behavior is enabled for that
 * pet (independent of the global {@code features.idle-sleep.enabled} flag) and the
 * per-pet {@code after-ticks} threshold overrides the global idle-seconds.
 */
public record PetStatesDefinition(
        PetStateDefinition moving,
        PetStateDefinition idle,
        PetStateDefinition sprinting,
        PetStateDefinition sleeping,
        PetStateDefinition attacking
) {

    /** Backward-compatible constructor for the original MOVING/IDLE schema. */
    public PetStatesDefinition(PetStateDefinition moving, PetStateDefinition idle) {
        this(moving, idle, null, null, null);
    }

    public boolean defined() {
        return moving != null || idle != null || sprinting != null || sleeping != null || attacking != null;
    }

    public PetStateDefinition definition(com.petsistemi.domain.animation.PetAnimationState state) {
        return switch (state) {
            case IDLE -> idle;
            case MOVING -> moving;
            case SPRINTING -> sprinting != null ? sprinting : moving;
            case SLEEPING -> sleeping != null ? sleeping : idle;
            case ATTACKING -> attacking;
        };
    }
}

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
 * </pre>
 * When {@code idle} is defined, the pet's idle/sleep behavior is enabled for that
 * pet (independent of the global {@code features.idle-sleep.enabled} flag) and the
 * per-pet {@code after-ticks} threshold overrides the global idle-seconds.
 */
public record PetStatesDefinition(PetStateDefinition moving, PetStateDefinition idle) {

    public boolean defined() {
        return moving != null || idle != null;
    }
}

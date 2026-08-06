package com.petsistemi.domain;

/** Idle/moving animation hint for per-pet {@code states:} definitions. */
public enum PetIdleAnimation {
    /** No special animation (idle visual disabled). */
    NONE,
    /** Sit down (Sittable mobs) or rest-scale for display pets. */
    SIT,
    /** Sleep near the owner (same rest visual; future: dedicated sleep pose). */
    SLEEP,
    /** Look around while idle (future: head/rotation tracking). */
    LOOK_AROUND,
    /** Moving-state animation (validated only; natural for mobs). */
    WALK
}

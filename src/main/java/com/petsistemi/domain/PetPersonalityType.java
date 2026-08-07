package com.petsistemi.domain;

/**
 * Defines personality archetypes for pets that affect follow distances, idle timers, and reactions.
 */
public enum PetPersonalityType {
    DEFAULT,
    LOYAL,     // Stays closer to owner, teleports sooner
    CURIOUS,   // Roams slightly further, higher wander frequency
    SHY,       // Retreats when damaged, stays further behind owner
    ENERGETIC, // Higher movement speed, shorter idle threshold
    SLEEPY;    // Enters idle/rest state 2x faster

    public static PetPersonalityType fromString(String name) {
        if (name == null) return DEFAULT;
        try {
            return PetPersonalityType.valueOf(name.toUpperCase(java.util.Locale.ROOT).trim());
        } catch (IllegalArgumentException e) {
            return DEFAULT;
        }
    }
}

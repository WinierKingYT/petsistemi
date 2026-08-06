package com.petsistemi.domain;

import java.util.Locale;

/** Runtime follow behavior of an active (spawned) pet. Persisted per selection; applied at summon. */
public enum PetFollowMode {
    FOLLOW,
    STAY,
    WANDER;

    public static PetFollowMode fromString(String raw) {
        if (raw == null) return null;
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}

package com.petsistemi.domain;

/** World time-of-day condition for {@code transforms[].when.time-of-day}. */
public enum PetTimeOfDay {
    /** World time below 13000 (or above 23000): day + sunrise. */
    DAY,
    /** World time in 13000..23000: night + sunset. */
    NIGHT
}

package com.petsistemi.domain;

/** Owner state conditions for {@code transforms[].when.owner-state}. */
public enum PetOwnerState {
    /** On the ground, not flying, not swimming. */
    WALKING,
    /** Flying (elytra or creative fly). */
    FLYING,
    /** Sneaking. */
    SNEAKING,
    /** In water. */
    IN_WATER,
    /** Falling (not on ground, falling velocity). */
    FALLING,
    /** Riding a vehicle. */
    RIDING
}

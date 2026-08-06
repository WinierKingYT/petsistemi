package com.petsistemi.domain;

/** Anchor configuration for {@code ANCHORED} movement. */
public record PetAnchorDefinition(
        PetAnchorPosition position,
        double distance,
        double height,
        boolean rotateWithOwner
) {

    public static final PetAnchorDefinition DEFAULT =
            new PetAnchorDefinition(PetAnchorPosition.BEHIND_RIGHT, 1.8, 0.4, true);

    public PetAnchorDefinition {
        position = position != null ? position : PetAnchorPosition.BEHIND_RIGHT;
        distance = sanitize(distance, 1.8);
        rotateWithOwner = rotateWithOwner;
    }

    private static double sanitize(double value, double fallback) {
        return (Double.isFinite(value) && value > 0.0) ? value : fallback;
    }
}

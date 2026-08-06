package com.petsistemi.domain;

/** Orbit movement configuration. */
public record PetOrbitDefinition(
        double radius,
        double height,
        double angularSpeed,
        boolean clockwise
) {

    public static final PetOrbitDefinition DEFAULT = new PetOrbitDefinition(1.7, 1.4, 1.2, true);

    public PetOrbitDefinition {
        radius = sanitize(radius, 1.7);
        height = sanitize(height, 1.4);
        angularSpeed = sanitize(angularSpeed, 1.2);
    }

    private static double sanitize(double value, double fallback) {
        if (Double.isFinite(value) && value > 0.0) {
            return value;
        }
        return fallback;
    }
}

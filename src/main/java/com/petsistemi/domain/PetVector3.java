package com.petsistemi.domain;

/** Simple immutable 3D vector for YAML-driven scale/offset configuration. */
public record PetVector3(double x, double y, double z) {

    public static final PetVector3 ZERO = new PetVector3(0.0, 0.0, 0.0);
    public static final PetVector3 ONE = new PetVector3(1.0, 1.0, 1.0);

    public PetVector3 {
        x = finiteOrZero(x);
        y = finiteOrZero(y);
        z = finiteOrZero(z);
    }

    private static double finiteOrZero(double value) {
        return Double.isFinite(value) ? value : 0.0;
    }

    public boolean isValidScale() {
        return x > 0.0 && y > 0.0 && z > 0.0;
    }
}

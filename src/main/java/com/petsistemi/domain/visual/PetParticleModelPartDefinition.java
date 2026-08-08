package com.petsistemi.domain.visual;

import com.petsistemi.domain.PetVector3;

/** One independently rotating particle shape within a particle model. */
public record PetParticleModelPartDefinition(
        PetParticleShape shape,
        String particleType,
        int points,
        double radius,
        double height,
        PetVector3 offset,
        double rotationSpeed
) {
    public PetParticleModelPartDefinition {
        if (shape == null) throw new IllegalArgumentException("Particle model shape zorunludur.");
        particleType = particleType == null ? null
                : particleType.trim().toUpperCase(java.util.Locale.ROOT);
        if (particleType == null || particleType.isBlank()) {
            throw new IllegalArgumentException("Particle model particle zorunludur.");
        }
        if (points < 3 || points > 256) {
            throw new IllegalArgumentException("Particle model points 3-256 aralığında olmalıdır.");
        }
        if (!Double.isFinite(radius) || radius <= 0.0 || radius > 8.0) {
            throw new IllegalArgumentException("Particle model radius 0-8 aralığında olmalıdır.");
        }
        if (!Double.isFinite(height) || height < 0.0 || height > 16.0) {
            throw new IllegalArgumentException("Particle model height 0-16 aralığında olmalıdır.");
        }
        if (!Double.isFinite(rotationSpeed) || Math.abs(rotationSpeed) > 360.0) {
            throw new IllegalArgumentException("Particle model rotation-speed -360..360 aralığında olmalıdır.");
        }
        offset = offset != null ? offset : PetVector3.ZERO;
    }
}

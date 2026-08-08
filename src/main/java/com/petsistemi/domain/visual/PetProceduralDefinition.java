package com.petsistemi.domain.visual;

import com.petsistemi.domain.PetRepresentationDefinition;

/** Runtime-generated persistent display graph and its motion parameters. */
public record PetProceduralDefinition(
        PetProceduralShape shape,
        int points,
        double radius,
        double height,
        double rotationSpeed,
        double pulseAmplitude,
        double pulseSpeed,
        int updateIntervalTicks,
        PetRepresentationDefinition content
) {
    public PetProceduralDefinition {
        if (shape == null) throw new IllegalArgumentException("Procedural shape zorunludur.");
        if (points < 3 || points > 32) {
            throw new IllegalArgumentException("Procedural points 3-32 aralığında olmalıdır.");
        }
        if (!Double.isFinite(radius) || radius <= 0.0 || radius > 8.0) {
            throw new IllegalArgumentException("Procedural radius 0-8 aralığında olmalıdır.");
        }
        if (!Double.isFinite(height) || height < 0.0 || height > 16.0) {
            throw new IllegalArgumentException("Procedural height 0-16 aralığında olmalıdır.");
        }
        if (!Double.isFinite(rotationSpeed) || Math.abs(rotationSpeed) > 360.0) {
            throw new IllegalArgumentException("Procedural rotation-speed -360..360 aralığında olmalıdır.");
        }
        if (!Double.isFinite(pulseAmplitude) || pulseAmplitude < 0.0 || pulseAmplitude > 1.0) {
            throw new IllegalArgumentException("Procedural pulse-amplitude 0-1 aralığında olmalıdır.");
        }
        if (!Double.isFinite(pulseSpeed) || Math.abs(pulseSpeed) > 360.0) {
            throw new IllegalArgumentException("Procedural pulse-speed -360..360 aralığında olmalıdır.");
        }
        if (updateIntervalTicks < 1 || updateIntervalTicks > 10) {
            throw new IllegalArgumentException("Procedural update-interval-ticks 1-10 aralığında olmalıdır.");
        }
        if (content == null) throw new IllegalArgumentException("Procedural content zorunludur.");
    }
}

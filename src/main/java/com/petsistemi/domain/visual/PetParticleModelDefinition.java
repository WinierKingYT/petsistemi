package com.petsistemi.domain.visual;

import java.util.List;

/** Bounded collection of procedural particle shapes emitted around one marker anchor. */
public record PetParticleModelDefinition(int updateIntervalTicks,
                                         List<PetParticleModelPartDefinition> parts) {
    public PetParticleModelDefinition {
        if (updateIntervalTicks < 1 || updateIntervalTicks > 20) {
            throw new IllegalArgumentException("Particle model update-interval-ticks 1-20 aralığında olmalıdır.");
        }
        parts = parts == null ? List.of() : List.copyOf(parts);
        if (parts.isEmpty() || parts.size() > 16) {
            throw new IllegalArgumentException("Particle model 1-16 part içermelidir.");
        }
        int totalPoints = parts.stream().mapToInt(PetParticleModelPartDefinition::points).sum();
        if (totalPoints > 256) {
            throw new IllegalArgumentException("Particle model toplam points değeri 256'yı aşamaz.");
        }
    }
}

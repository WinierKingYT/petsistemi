package com.petsistemi.domain;

/**
 * Visual and sound effects rendered when a pet is spawned/summoned or despawned/dismissed.
 */
public record PetSpawnStyleDefinition(
        String type,
        String entryParticle,
        int entryParticleCount,
        String entrySound,
        String exitParticle,
        int exitParticleCount,
        String exitSound
) {
    public static final PetSpawnStyleDefinition DEFAULT = new PetSpawnStyleDefinition(
            "INSTANT", "PORTAL", 25, "ENTITY_ENDERMAN_TELEPORT", "SMOKE_LARGE", 20, "ENTITY_ITEM_BREAK"
    );
}

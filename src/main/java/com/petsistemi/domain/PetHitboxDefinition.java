package com.petsistemi.domain;

/**
 * Definition of an interaction hitbox (Paper Interaction entity) for non-entity pets.
 */
public record PetHitboxDefinition(
        boolean enabled,
        float width,
        float height
) {
    public static final PetHitboxDefinition DEFAULT = new PetHitboxDefinition(true, 0.6f, 0.8f);
    public static final PetHitboxDefinition DISABLED = new PetHitboxDefinition(false, 0.0f, 0.0f);
}

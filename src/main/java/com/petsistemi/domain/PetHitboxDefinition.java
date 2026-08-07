package com.petsistemi.domain;

/**
 * Definition of an interaction hitbox (Paper Interaction entity) for non-entity pets.
 * Defaults to disabled to prevent invisible hitbox clutter in crowded areas.
 */
public record PetHitboxDefinition(
        boolean enabled,
        float width,
        float height
) {
    public static final PetHitboxDefinition DEFAULT = new PetHitboxDefinition(false, 0.0f, 0.0f);
    public static final PetHitboxDefinition DISABLED = new PetHitboxDefinition(false, 0.0f, 0.0f);
}

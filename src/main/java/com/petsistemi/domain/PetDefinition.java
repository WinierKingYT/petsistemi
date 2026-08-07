package com.petsistemi.domain;

import java.util.List;

public record PetDefinition(
        String id,
        String displayName,
        List<String> description,
        String entityType,
        boolean baby,
        boolean glowing,
        boolean invulnerable,
        boolean silent,
        boolean gravity,
        boolean progressionEnabled,
        int maxLevel,
        boolean nameplateEnabled,
        List<String> nameplateFormat,
        PetRepresentationDefinition representation,
        PetMovementDefinition movement,
        PetStatesDefinition states,
        java.util.List<PetTransformDefinition> transforms,
        java.util.Map<PetReactionType, PetReactionDefinition> reactions,
        java.util.Map<String, PetEmoteDefinition> emotes,
        /** GUI listesinde kullanılan material adı ({@code gui-material}); null → id'ye göre varsayılan. */
        String guiMaterial,
        /** Bu peti kullanma yetkisi ({@code permission}); null → kısıtlama yok. */
        String permission,
        List<PetBuffDefinition> buffs,
        PetPersonalityType personality,
        List<PetEvolutionDefinition> evolutions,
        PetHitboxDefinition hitbox,
        List<PetLevelRewardDefinition> levelRewards,
        List<PetFollowMode> allowedModes,
        PetSpawnStyleDefinition spawnStyle,
        PetMountDefinition mount,
        PetPresenceDefinition presence
) {

    /** Backward-compatible canonical overload without new fields. */
    public PetDefinition(
            String id,
            String displayName,
            List<String> description,
            String entityType,
            boolean baby,
            boolean glowing,
            boolean invulnerable,
            boolean silent,
            boolean gravity,
            boolean progressionEnabled,
            int maxLevel,
            boolean nameplateEnabled,
            List<String> nameplateFormat,
            PetRepresentationDefinition representation,
            PetMovementDefinition movement,
            PetStatesDefinition states,
            java.util.List<PetTransformDefinition> transforms,
            java.util.Map<PetReactionType, PetReactionDefinition> reactions,
            java.util.Map<String, PetEmoteDefinition> emotes,
            String guiMaterial,
            String permission
    ) {
        this(id, displayName, description, entityType, baby, glowing, invulnerable, silent, gravity,
                progressionEnabled, maxLevel, nameplateEnabled, nameplateFormat, representation, movement,
                states, transforms, reactions, emotes, guiMaterial, permission, null, PetPersonalityType.DEFAULT, null, null, null, null, null, null, null);
    }

    /** Backward-compatible constructor — defaults to a classic ENTITY pet with legacy flags. */
    public PetDefinition(
            String id,
            String displayName,
            List<String> description,
            String entityType,
            boolean baby,
            boolean glowing,
            boolean invulnerable,
            boolean silent,
            boolean gravity,
            boolean progressionEnabled,
            int maxLevel,
            boolean nameplateEnabled,
            List<String> nameplateFormat
    ) {
        this(id, displayName, description, entityType, baby, glowing, invulnerable, silent, gravity,
                progressionEnabled, maxLevel, nameplateEnabled, nameplateFormat, null, null, null, null, null, null, null, null);
    }

    /** Backward-compatible constructor with movement but without per-pet states/transforms. */
    public PetDefinition(
            String id,
            String displayName,
            List<String> description,
            String entityType,
            boolean baby,
            boolean glowing,
            boolean invulnerable,
            boolean silent,
            boolean gravity,
            boolean progressionEnabled,
            int maxLevel,
            boolean nameplateEnabled,
            List<String> nameplateFormat,
            PetRepresentationDefinition representation,
            PetMovementDefinition movement
    ) {
        this(id, displayName, description, entityType, baby, glowing, invulnerable, silent, gravity,
                progressionEnabled, maxLevel, nameplateEnabled, nameplateFormat, representation, movement, null, null, null, null, null, null);
    }

    /** Convenience constructor with states but without transforms. */
    public PetDefinition(
            String id,
            String displayName,
            List<String> description,
            String entityType,
            boolean baby,
            boolean glowing,
            boolean invulnerable,
            boolean silent,
            boolean gravity,
            boolean progressionEnabled,
            int maxLevel,
            boolean nameplateEnabled,
            List<String> nameplateFormat,
            PetRepresentationDefinition representation,
            PetMovementDefinition movement,
            PetStatesDefinition states
    ) {
        this(id, displayName, description, entityType, baby, glowing, invulnerable, silent, gravity,
                progressionEnabled, maxLevel, nameplateEnabled, nameplateFormat, representation, movement, states, null, null, null, null, null);
    }

    /** Convenience constructor with states and transforms but without reactions/emotes. */
    public PetDefinition(
            String id,
            String displayName,
            List<String> description,
            String entityType,
            boolean baby,
            boolean glowing,
            boolean invulnerable,
            boolean silent,
            boolean gravity,
            boolean progressionEnabled,
            int maxLevel,
            boolean nameplateEnabled,
            List<String> nameplateFormat,
            PetRepresentationDefinition representation,
            PetMovementDefinition movement,
            PetStatesDefinition states,
            java.util.List<PetTransformDefinition> transforms
    ) {
        this(id, displayName, description, entityType, baby, glowing, invulnerable, silent, gravity,
                progressionEnabled, maxLevel, nameplateEnabled, nameplateFormat, representation, movement, states, transforms, null, null, null, null);
    }

    /** Convenience constructor without the {@code gui-material}/{@code permission} metadata. */
    public PetDefinition(
            String id,
            String displayName,
            List<String> description,
            String entityType,
            boolean baby,
            boolean glowing,
            boolean invulnerable,
            boolean silent,
            boolean gravity,
            boolean progressionEnabled,
            int maxLevel,
            boolean nameplateEnabled,
            List<String> nameplateFormat,
            PetRepresentationDefinition representation,
            PetMovementDefinition movement,
            PetStatesDefinition states,
            java.util.List<PetTransformDefinition> transforms,
            java.util.Map<PetReactionType, PetReactionDefinition> reactions,
            java.util.Map<String, PetEmoteDefinition> emotes
    ) {
        this(id, displayName, description, entityType, baby, glowing, invulnerable, silent, gravity,
                progressionEnabled, maxLevel, nameplateEnabled, nameplateFormat, representation, movement,
                states, transforms, reactions, emotes, null, null);
    }

    public PetRepresentationDefinition representationOrEntity() {
        if (representation != null) {
            return representation;
        }
        return PetRepresentationDefinition.legacyEntity(entityType, baby, glowing, invulnerable, silent, gravity);
    }

    /**
     * Returns a copy of this definition with the given transform's visual overrides
     * applied to the representation. State/transforms/movement stay untouched.
     */
    public PetDefinition withTransformApplied(PetTransformDefinition transform) {
        if (transform == null || transform.apply() == null || !transform.apply().hasAny()) {
            return this;
        }
        return new PetDefinition(id, displayName, description, entityType, baby, glowing, invulnerable,
                silent, gravity, progressionEnabled, maxLevel, nameplateEnabled, nameplateFormat,
                representationOrEntity().applyOverride(transform.apply()), movement, states, transforms, reactions, emotes,
                guiMaterial, permission, buffs, personality, evolutions, hitbox, levelRewards, allowedModes, spawnStyle, mount, presence);
    }
}

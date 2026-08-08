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
        PetPresenceDefinition presence,
        List<com.petsistemi.domain.behavior.PetBehaviorDefinition> behaviors,
        java.util.Map<org.bukkit.NamespacedKey, com.petsistemi.domain.ability.PetAbilityDefinition> abilities,
        List<com.petsistemi.domain.item.PetItemActionDefinition> itemActions
) {

    /** Backward-compatible overload retaining the former ability-aware signature. */
    public PetDefinition(
            String id, String displayName, List<String> description, String entityType,
            boolean baby, boolean glowing, boolean invulnerable, boolean silent, boolean gravity,
            boolean progressionEnabled, int maxLevel, boolean nameplateEnabled, List<String> nameplateFormat,
            PetRepresentationDefinition representation, PetMovementDefinition movement, PetStatesDefinition states,
            java.util.List<PetTransformDefinition> transforms,
            java.util.Map<PetReactionType, PetReactionDefinition> reactions,
            java.util.Map<String, PetEmoteDefinition> emotes, String guiMaterial, String permission,
            List<PetBuffDefinition> buffs, PetPersonalityType personality, List<PetEvolutionDefinition> evolutions,
            PetHitboxDefinition hitbox, List<PetLevelRewardDefinition> levelRewards, List<PetFollowMode> allowedModes,
            PetSpawnStyleDefinition spawnStyle, PetMountDefinition mount, PetPresenceDefinition presence,
            List<com.petsistemi.domain.behavior.PetBehaviorDefinition> behaviors,
            java.util.Map<org.bukkit.NamespacedKey, com.petsistemi.domain.ability.PetAbilityDefinition> abilities
    ) {
        this(id, displayName, description, entityType, baby, glowing, invulnerable, silent, gravity,
                progressionEnabled, maxLevel, nameplateEnabled, nameplateFormat, representation, movement,
                states, transforms, reactions, emotes, guiMaterial, permission, buffs, personality, evolutions,
                hitbox, levelRewards, allowedModes, spawnStyle, mount, presence, behaviors, abilities, null);
    }

    /** Backward-compatible overload retaining the former behavior-aware signature. */
    public PetDefinition(
            String id, String displayName, List<String> description, String entityType,
            boolean baby, boolean glowing, boolean invulnerable, boolean silent, boolean gravity,
            boolean progressionEnabled, int maxLevel, boolean nameplateEnabled, List<String> nameplateFormat,
            PetRepresentationDefinition representation, PetMovementDefinition movement, PetStatesDefinition states,
            java.util.List<PetTransformDefinition> transforms,
            java.util.Map<PetReactionType, PetReactionDefinition> reactions,
            java.util.Map<String, PetEmoteDefinition> emotes, String guiMaterial, String permission,
            List<PetBuffDefinition> buffs, PetPersonalityType personality, List<PetEvolutionDefinition> evolutions,
            PetHitboxDefinition hitbox, List<PetLevelRewardDefinition> levelRewards, List<PetFollowMode> allowedModes,
            PetSpawnStyleDefinition spawnStyle, PetMountDefinition mount, PetPresenceDefinition presence,
            List<com.petsistemi.domain.behavior.PetBehaviorDefinition> behaviors
    ) {
        this(id, displayName, description, entityType, baby, glowing, invulnerable, silent, gravity,
                progressionEnabled, maxLevel, nameplateEnabled, nameplateFormat, representation, movement,
                states, transforms, reactions, emotes, guiMaterial, permission, buffs, personality, evolutions,
                hitbox, levelRewards, allowedModes, spawnStyle, mount, presence, behaviors, null);
    }

    /** Backward-compatible overload retaining the former 30-component canonical signature. */
    public PetDefinition(
            String id, String displayName, List<String> description, String entityType,
            boolean baby, boolean glowing, boolean invulnerable, boolean silent, boolean gravity,
            boolean progressionEnabled, int maxLevel, boolean nameplateEnabled, List<String> nameplateFormat,
            PetRepresentationDefinition representation, PetMovementDefinition movement, PetStatesDefinition states,
            java.util.List<PetTransformDefinition> transforms,
            java.util.Map<PetReactionType, PetReactionDefinition> reactions,
            java.util.Map<String, PetEmoteDefinition> emotes, String guiMaterial, String permission,
            List<PetBuffDefinition> buffs, PetPersonalityType personality, List<PetEvolutionDefinition> evolutions,
            PetHitboxDefinition hitbox, List<PetLevelRewardDefinition> levelRewards, List<PetFollowMode> allowedModes,
            PetSpawnStyleDefinition spawnStyle, PetMountDefinition mount, PetPresenceDefinition presence
    ) {
        this(id, displayName, description, entityType, baby, glowing, invulnerable, silent, gravity,
                progressionEnabled, maxLevel, nameplateEnabled, nameplateFormat, representation, movement,
                states, transforms, reactions, emotes, guiMaterial, permission, buffs, personality, evolutions,
                hitbox, levelRewards, allowedModes, spawnStyle, mount, presence, null, null);
    }

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
        // Only the representation changes; toBuilder() carries the other 29 components so a
        // newly added field can never be silently dropped from a transformed definition.
        return toBuilder()
                .representation(representationOrEntity().applyOverride(transform.apply()))
                .build();
    }

    // ── Builder ──────────────────────────────────────────────────────────────────
    //
    // This record has 30 components, most of them optional. Positional construction
    // forced every caller — and every test — to thread a long run of nulls, where a
    // single misplaced argument silently binds the wrong field instead of failing to
    // compile. The builder names each field at the call site and defaults the rest.

    /** Starts a builder with the two fields every pet must have. */
    public static Builder builder(String id, String displayName) {
        return new Builder(id, displayName);
    }

    /** Starts a builder pre-filled with this definition's values, for copy-with-changes. */
    public Builder toBuilder() {
        return copyBuilder(displayName);
    }

    private Builder copyBuilder(String copiedDisplayName) {
        return new Builder(id, copiedDisplayName)
                .description(description)
                .entityType(entityType)
                .baby(baby).glowing(glowing).invulnerable(invulnerable).silent(silent).gravity(gravity)
                .progressionEnabled(progressionEnabled).maxLevel(maxLevel)
                .nameplateEnabled(nameplateEnabled).nameplateFormat(nameplateFormat)
                .representation(representation).movement(movement).states(states)
                .transforms(transforms).reactions(reactions).emotes(emotes)
                .guiMaterial(guiMaterial).permission(permission)
                .buffs(buffs).personality(personality).evolutions(evolutions).hitbox(hitbox)
                .levelRewards(levelRewards).allowedModes(allowedModes)
                .spawnStyle(spawnStyle).mount(mount).presence(presence).behaviors(behaviors).abilities(abilities)
                .itemActions(itemActions);
    }

    /** Applies a level-derived evolution stage without changing the persisted pet identity. */
    public PetDefinition withEvolutionApplied(PetEvolutionDefinition evolution, PetDefinition target) {
        if (evolution == null) return this;
        PetDefinition source = target != null ? target : this;
        String evolvedName = evolution.displayNameOverride() != null
                ? evolution.displayNameOverride() : source.displayName();
        Builder builder = source.copyBuilder(evolvedName);
        if (evolution.scaleOverride() != null) {
            PetVisualOverride override = new PetVisualOverride(null, null, null,
                    evolution.scaleOverride(), null, null, null, null, null);
            builder.representation(source.representationOrEntity().applyOverride(override));
        }
        return builder.build();
    }

    public static final class Builder {
        private final String id;
        private final String displayName;
        private List<String> description = List.of();
        private String entityType = "WOLF";
        private boolean baby;
        private boolean glowing;
        private boolean invulnerable = true;
        private boolean silent;
        private boolean gravity = true;
        private boolean progressionEnabled = true;
        private int maxLevel = 100;
        private boolean nameplateEnabled = true;
        private List<String> nameplateFormat = List.of("{pet_name}");
        private PetRepresentationDefinition representation;
        private PetMovementDefinition movement;
        private PetStatesDefinition states;
        private java.util.List<PetTransformDefinition> transforms;
        private java.util.Map<PetReactionType, PetReactionDefinition> reactions;
        private java.util.Map<String, PetEmoteDefinition> emotes;
        private String guiMaterial;
        private String permission;
        private List<PetBuffDefinition> buffs;
        private PetPersonalityType personality;
        private List<PetEvolutionDefinition> evolutions;
        private PetHitboxDefinition hitbox;
        private List<PetLevelRewardDefinition> levelRewards;
        private List<PetFollowMode> allowedModes;
        private PetSpawnStyleDefinition spawnStyle;
        private PetMountDefinition mount;
        private PetPresenceDefinition presence;
        private List<com.petsistemi.domain.behavior.PetBehaviorDefinition> behaviors;
        private java.util.Map<org.bukkit.NamespacedKey, com.petsistemi.domain.ability.PetAbilityDefinition> abilities;
        private List<com.petsistemi.domain.item.PetItemActionDefinition> itemActions;

        private Builder(String id, String displayName) {
            this.id = id;
            this.displayName = displayName;
        }

        public Builder description(List<String> v) { this.description = v != null ? v : List.of(); return this; }
        public Builder entityType(String v) { this.entityType = v; return this; }
        public Builder baby(boolean v) { this.baby = v; return this; }
        public Builder glowing(boolean v) { this.glowing = v; return this; }
        public Builder invulnerable(boolean v) { this.invulnerable = v; return this; }
        public Builder silent(boolean v) { this.silent = v; return this; }
        public Builder gravity(boolean v) { this.gravity = v; return this; }
        public Builder progressionEnabled(boolean v) { this.progressionEnabled = v; return this; }
        public Builder maxLevel(int v) { this.maxLevel = v; return this; }
        public Builder nameplateEnabled(boolean v) { this.nameplateEnabled = v; return this; }
        public Builder nameplateFormat(List<String> v) { this.nameplateFormat = v != null ? v : List.of(); return this; }
        public Builder representation(PetRepresentationDefinition v) { this.representation = v; return this; }
        public Builder movement(PetMovementDefinition v) { this.movement = v; return this; }
        public Builder states(PetStatesDefinition v) { this.states = v; return this; }
        public Builder transforms(java.util.List<PetTransformDefinition> v) { this.transforms = v; return this; }
        public Builder reactions(java.util.Map<PetReactionType, PetReactionDefinition> v) { this.reactions = v; return this; }
        public Builder emotes(java.util.Map<String, PetEmoteDefinition> v) { this.emotes = v; return this; }
        public Builder guiMaterial(String v) { this.guiMaterial = v; return this; }
        public Builder permission(String v) { this.permission = v; return this; }
        public Builder buffs(List<PetBuffDefinition> v) { this.buffs = v; return this; }
        public Builder personality(PetPersonalityType v) { this.personality = v; return this; }
        public Builder evolutions(List<PetEvolutionDefinition> v) { this.evolutions = v; return this; }
        public Builder hitbox(PetHitboxDefinition v) { this.hitbox = v; return this; }
        public Builder levelRewards(List<PetLevelRewardDefinition> v) { this.levelRewards = v; return this; }
        public Builder allowedModes(List<PetFollowMode> v) { this.allowedModes = v; return this; }
        public Builder spawnStyle(PetSpawnStyleDefinition v) { this.spawnStyle = v; return this; }
        public Builder mount(PetMountDefinition v) { this.mount = v; return this; }
        public Builder presence(PetPresenceDefinition v) { this.presence = v; return this; }
        public Builder behaviors(List<com.petsistemi.domain.behavior.PetBehaviorDefinition> v) { this.behaviors = v; return this; }
        public Builder abilities(java.util.Map<org.bukkit.NamespacedKey, com.petsistemi.domain.ability.PetAbilityDefinition> v) { this.abilities = v; return this; }
        public Builder itemActions(List<com.petsistemi.domain.item.PetItemActionDefinition> v) { this.itemActions = v; return this; }

        public PetDefinition build() {
            return new PetDefinition(id, displayName, description, entityType,
                    baby, glowing, invulnerable, silent, gravity,
                    progressionEnabled, maxLevel, nameplateEnabled, nameplateFormat,
                    representation, movement, states, transforms, reactions, emotes,
                    guiMaterial, permission, buffs, personality, evolutions, hitbox,
                    levelRewards, allowedModes, spawnStyle, mount, presence, behaviors, abilities, itemActions);
        }
    }
}

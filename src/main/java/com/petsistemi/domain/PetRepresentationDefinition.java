package com.petsistemi.domain;

/**
 * Visual representation definition of a pet. Fully YAML-driven; the same runtime
 * can host entity pets, item/block/text displays, particle auras and multi-entity swarms.
 *
 * <p>{@code null} fields mean "use runtime defaults".</p>
 */
public record PetRepresentationDefinition(
        RuntimeRepresentationType type,
        String entityType,
        boolean baby,
        boolean glowing,
        boolean invulnerable,
        boolean silent,
        boolean gravity,
        String itemMaterial,
        Integer customModelData,
        PetVector3 scale,
        String particleType,
        int particleCount,
        double particleOffset,
        double particleSpeed,
        int childCount,
        String childMaterial
) {

    /** Backward-compatible constructor (pre-Milestone-2 fields only). */
    public PetRepresentationDefinition(
            RuntimeRepresentationType type,
            String entityType,
            boolean baby,
            boolean glowing,
            boolean invulnerable,
            boolean silent,
            boolean gravity,
            String itemMaterial,
            Integer customModelData,
            PetVector3 scale
    ) {
        this(type, entityType, baby, glowing, invulnerable, silent, gravity, itemMaterial,
                customModelData, scale, null, 0, 0.0, 0.0, 0, null);
    }

    public static PetRepresentationDefinition legacyEntity(String entityType, boolean baby, boolean glowing,
                                                           boolean invulnerable, boolean silent, boolean gravity) {
        return new PetRepresentationDefinition(RuntimeRepresentationType.ENTITY, entityType, baby, glowing,
                invulnerable, silent, gravity, null, null, PetVector3.ONE);
    }

    public static PetRepresentationDefinition display(RuntimeRepresentationType type, String itemMaterial,
                                                      Integer customModelData, PetVector3 scale) {
        return new PetRepresentationDefinition(type, null, false, false, true, true, false,
                itemMaterial, customModelData, scale != null ? scale : PetVector3.ONE);
    }

    /**
     * Returns a copy of this representation with the transform overrides applied
     * (only non-null override fields replace the base values).
     */
    public PetRepresentationDefinition applyOverride(PetVisualOverride override) {
        if (override == null || !override.hasAny()) {
            return this;
        }
        String item;
        if (type == RuntimeRepresentationType.BLOCK_DISPLAY) {
            item = override.blockMaterial() != null ? override.blockMaterial() : itemMaterial;
        } else {
            item = override.itemMaterial() != null ? override.itemMaterial() : itemMaterial;
        }
        String particle = override.particleType() != null ? override.particleType() : particleType;
        int count = override.particleCount() != null ? override.particleCount() : particleCount;
        Integer cmd = override.customModelData() != null ? override.customModelData() : customModelData;
        boolean glow = override.glowing() != null ? override.glowing() : glowing;
        boolean babyFlag = override.baby() != null ? override.baby() : baby;
        PetVector3 s = override.scale() != null ? override.scale() : scale;
        String eType = override.entityType() != null ? override.entityType() : entityType;
        return new PetRepresentationDefinition(type, eType, babyFlag, glow, invulnerable, silent, gravity,
                item, cmd, s, particle, count, particleOffset, particleSpeed, childCount, childMaterial);
    }
}

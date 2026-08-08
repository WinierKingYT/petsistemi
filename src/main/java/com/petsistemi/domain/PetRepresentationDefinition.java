package com.petsistemi.domain;

import com.petsistemi.domain.visual.PetVisualGraphDefinition;
import com.petsistemi.domain.visual.PetDisplayModelDefinition;
import com.petsistemi.domain.visual.PetSpriteDefinition;
import com.petsistemi.domain.visual.PetParticleModelDefinition;
import com.petsistemi.domain.visual.PetProceduralDefinition;
import org.bukkit.NamespacedKey;

/**
 * Visual representation definition of a pet. Fully YAML-driven; the same runtime
 * can host entity pets, item/block/text displays, particle auras and multi-entity swarms.
 *
 * <p>{@code null} fields mean "use runtime defaults".</p>
 */
public record PetRepresentationDefinition(
        RuntimeRepresentationType type,
        NamespacedKey key,
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
        String childMaterial,
        /** Provider asset id for namespaced external representations. */
        String modelId,
        /** Named component graph for COMPOSITE; null for atomic representations. */
        PetVisualGraphDefinition visualGraph,
        /** Skeleton and animation channels for DISPLAY_MODEL. */
        PetDisplayModelDefinition displayModel,
        /** ItemDisplay-backed billboard and state frame data for SPRITE. */
        PetSpriteDefinition sprite,
        /** Procedural shape collection for PARTICLE_MODEL. */
        PetParticleModelDefinition particleModel,
        /** Persistent generated display graph for PROCEDURAL. */
        PetProceduralDefinition procedural
) {

    /** Backward-compatible overload retaining the former 22-component canonical signature. */
    public PetRepresentationDefinition(
            RuntimeRepresentationType type, NamespacedKey key, String entityType,
            boolean baby, boolean glowing, boolean invulnerable, boolean silent, boolean gravity,
            String itemMaterial, Integer customModelData, PetVector3 scale, String particleType,
            int particleCount, double particleOffset, double particleSpeed, int childCount,
            String childMaterial, String modelId, PetVisualGraphDefinition visualGraph,
            PetDisplayModelDefinition displayModel, PetSpriteDefinition sprite,
            PetParticleModelDefinition particleModel
    ) {
        this(type, key, entityType, baby, glowing, invulnerable, silent, gravity, itemMaterial,
                customModelData, scale, particleType, particleCount, particleOffset, particleSpeed,
                childCount, childMaterial, modelId, visualGraph, displayModel, sprite, particleModel, null);
    }

    /** Backward-compatible overload retaining the former 21-component canonical signature. */
    public PetRepresentationDefinition(
            RuntimeRepresentationType type, NamespacedKey key, String entityType,
            boolean baby, boolean glowing, boolean invulnerable, boolean silent, boolean gravity,
            String itemMaterial, Integer customModelData, PetVector3 scale, String particleType,
            int particleCount, double particleOffset, double particleSpeed, int childCount,
            String childMaterial, String modelId, PetVisualGraphDefinition visualGraph,
            PetDisplayModelDefinition displayModel, PetSpriteDefinition sprite
    ) {
        this(type, key, entityType, baby, glowing, invulnerable, silent, gravity, itemMaterial,
                customModelData, scale, particleType, particleCount, particleOffset, particleSpeed,
                childCount, childMaterial, modelId, visualGraph, displayModel, sprite, null);
    }

    /** Backward-compatible overload retaining the former 20-component canonical signature. */
    public PetRepresentationDefinition(
            RuntimeRepresentationType type, NamespacedKey key, String entityType,
            boolean baby, boolean glowing, boolean invulnerable, boolean silent, boolean gravity,
            String itemMaterial, Integer customModelData, PetVector3 scale, String particleType,
            int particleCount, double particleOffset, double particleSpeed, int childCount,
            String childMaterial, String modelId, PetVisualGraphDefinition visualGraph,
            PetDisplayModelDefinition displayModel
    ) {
        this(type, key, entityType, baby, glowing, invulnerable, silent, gravity, itemMaterial,
                customModelData, scale, particleType, particleCount, particleOffset, particleSpeed,
                childCount, childMaterial, modelId, visualGraph, displayModel, null);
    }

    /** Backward-compatible overload retaining the former 19-component canonical signature. */
    public PetRepresentationDefinition(
            RuntimeRepresentationType type, NamespacedKey key, String entityType,
            boolean baby, boolean glowing, boolean invulnerable, boolean silent, boolean gravity,
            String itemMaterial, Integer customModelData, PetVector3 scale, String particleType,
            int particleCount, double particleOffset, double particleSpeed, int childCount,
            String childMaterial, String modelId, PetVisualGraphDefinition visualGraph
    ) {
        this(type, key, entityType, baby, glowing, invulnerable, silent, gravity, itemMaterial,
                customModelData, scale, particleType, particleCount, particleOffset, particleSpeed,
                childCount, childMaterial, modelId, visualGraph, null);
    }

    /** Backward-compatible overload retaining the former 18-component canonical signature. */
    public PetRepresentationDefinition(
            RuntimeRepresentationType type, NamespacedKey key, String entityType,
            boolean baby, boolean glowing, boolean invulnerable, boolean silent, boolean gravity,
            String itemMaterial, Integer customModelData, PetVector3 scale, String particleType,
            int particleCount, double particleOffset, double particleSpeed, int childCount,
            String childMaterial, String modelId
    ) {
        this(type, key, entityType, baby, glowing, invulnerable, silent, gravity, itemMaterial,
                customModelData, scale, particleType, particleCount, particleOffset, particleSpeed,
                childCount, childMaterial, modelId, null);
    }

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
        this(type, RuntimeKeyResolver.representationKey(type), entityType, baby, glowing, invulnerable, silent, gravity, itemMaterial,
                customModelData, scale, null, 0, 0.0, 0.0, 0, null, null, null);
    }

    /** Backward-compatible constructor retaining the former canonical signature. */
    public PetRepresentationDefinition(RuntimeRepresentationType type, String entityType, boolean baby, boolean glowing,
                                       boolean invulnerable, boolean silent, boolean gravity, String itemMaterial,
                                       Integer customModelData, PetVector3 scale, String particleType, int particleCount,
                                       double particleOffset, double particleSpeed, int childCount, String childMaterial) {
        this(type, RuntimeKeyResolver.representationKey(type), entityType, baby, glowing, invulnerable, silent, gravity,
                itemMaterial, customModelData, scale, particleType, particleCount, particleOffset, particleSpeed,
                childCount, childMaterial, null, null);
    }

    /** Extension-aware constructor; the enum remains a compatibility hint for legacy consumers. */
    public PetRepresentationDefinition(NamespacedKey key, String entityType, boolean baby, boolean glowing,
                                       boolean invulnerable, boolean silent, boolean gravity, String itemMaterial,
                                       Integer customModelData, PetVector3 scale, String particleType, int particleCount,
                                       double particleOffset, double particleSpeed, int childCount, String childMaterial) {
        this(RuntimeKeyResolver.builtInRepresentation(key) != null ? RuntimeKeyResolver.builtInRepresentation(key)
                        : RuntimeRepresentationType.ENTITY,
                key, entityType, baby, glowing, invulnerable, silent, gravity, itemMaterial, customModelData, scale,
                particleType, particleCount, particleOffset, particleSpeed, childCount, childMaterial, null, null);
    }

    /** Extension-aware constructor with a provider asset id. */
    public PetRepresentationDefinition(NamespacedKey key, String modelId, String entityType, boolean baby,
                                       boolean glowing, boolean invulnerable, boolean silent, boolean gravity,
                                       String itemMaterial, Integer customModelData, PetVector3 scale,
                                       String particleType, int particleCount, double particleOffset,
                                       double particleSpeed, int childCount, String childMaterial) {
        this(RuntimeKeyResolver.builtInRepresentation(key) != null ? RuntimeKeyResolver.builtInRepresentation(key)
                        : RuntimeRepresentationType.ENTITY,
                key, entityType, baby, glowing, invulnerable, silent, gravity, itemMaterial, customModelData, scale,
                particleType, particleCount, particleOffset, particleSpeed, childCount, childMaterial, modelId, null);
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

    public static PetRepresentationDefinition composite(PetVisualGraphDefinition graph) {
        return new PetRepresentationDefinition(RuntimeRepresentationType.COMPOSITE,
                RuntimeKeyResolver.representationKey(RuntimeRepresentationType.COMPOSITE),
                "MARKER", false, false, true, true, false, null, null, PetVector3.ONE,
                null, 0, 0.0, 0.0, 0, null, null, graph);
    }

    public static PetRepresentationDefinition displayModel(PetDisplayModelDefinition model) {
        return new PetRepresentationDefinition(RuntimeRepresentationType.DISPLAY_MODEL,
                RuntimeKeyResolver.representationKey(RuntimeRepresentationType.DISPLAY_MODEL),
                "MARKER", false, false, true, true, false, null, null, PetVector3.ONE,
                null, 0, 0.0, 0.0, 0, null, null, null, model);
    }

    public static PetRepresentationDefinition sprite(PetSpriteDefinition sprite, PetVector3 scale) {
        return new PetRepresentationDefinition(RuntimeRepresentationType.SPRITE,
                RuntimeKeyResolver.representationKey(RuntimeRepresentationType.SPRITE),
                "MARKER", false, false, true, true, false, sprite.material(), null,
                scale != null ? scale : PetVector3.ONE, null, 0, 0.0, 0.0, 0, null,
                null, null, null, sprite);
    }

    public static PetRepresentationDefinition particleModel(PetParticleModelDefinition particleModel) {
        return new PetRepresentationDefinition(RuntimeRepresentationType.PARTICLE_MODEL,
                RuntimeKeyResolver.representationKey(RuntimeRepresentationType.PARTICLE_MODEL),
                "MARKER", false, false, true, true, false, null, null, PetVector3.ONE,
                null, 0, 0.0, 0.0, 0, null, null, null, null, null, particleModel);
    }

    public static PetRepresentationDefinition procedural(PetProceduralDefinition procedural, PetVector3 scale) {
        return new PetRepresentationDefinition(RuntimeRepresentationType.PROCEDURAL,
                RuntimeKeyResolver.representationKey(RuntimeRepresentationType.PROCEDURAL),
                "MARKER", false, false, true, true, false, null, null,
                scale != null ? scale : PetVector3.ONE, null, 0, 0.0, 0.0, 0, null,
                null, null, null, null, null, procedural);
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
        return new PetRepresentationDefinition(type, key, eType, babyFlag, glow, invulnerable, silent, gravity,
                item, cmd, s, particle, count, particleOffset, particleSpeed, childCount, childMaterial, modelId,
                visualGraph, displayModel, sprite, particleModel, procedural);
    }
}

package com.petsistemi.domain;

/**
 * Visual overrides applied by a transform ({@code transforms[].apply.representation}).
 * {@code null} fields mean "keep the base definition's value".
 */
public record PetVisualOverride(
        String itemMaterial,
        String blockMaterial,
        Integer customModelData,
        PetVector3 scale,
        String particleType,
        Integer particleCount,
        Boolean glowing,
        Boolean baby,
        String entityType
) {

    public PetVisualOverride(
            String itemMaterial,
            String blockMaterial,
            Integer customModelData,
            PetVector3 scale,
            String particleType,
            Integer particleCount,
            Boolean glowing,
            Boolean baby
    ) {
        this(itemMaterial, blockMaterial, customModelData, scale, particleType, particleCount, glowing, baby, null);
    }

    public boolean hasAny() {
        return itemMaterial != null
                || blockMaterial != null
                || customModelData != null
                || scale != null
                || particleType != null
                || particleCount != null
                || glowing != null
                || baby != null
                || entityType != null;
    }
}

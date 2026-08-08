package com.petsistemi.domain.visual;

import com.petsistemi.domain.PetRepresentationDefinition;

/** One named node in a composable pet visual graph. */
public record PetVisualNodeDefinition(String id, String parentId,
                                      PetRepresentationDefinition representation,
                                      PetVisualTransform transform) {
    public PetVisualNodeDefinition {
        id = normalize(id);
        parentId = parentId == null || parentId.isBlank() ? null : normalize(parentId);
        if (!id.matches("[a-z0-9][a-z0-9._-]{0,63}")) {
            throw new IllegalArgumentException("Geçersiz visual node id: " + id);
        }
        if (parentId != null && !parentId.matches("[a-z0-9][a-z0-9._-]{0,63}")) {
            throw new IllegalArgumentException("Geçersiz visual parent node id: " + parentId);
        }
        if (representation == null) throw new IllegalArgumentException("Visual node representation eksik: " + id);
        transform = transform != null ? transform : PetVisualTransform.IDENTITY;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
    }
}

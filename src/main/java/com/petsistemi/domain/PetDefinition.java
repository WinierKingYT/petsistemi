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
        List<String> nameplateFormat
) {
}

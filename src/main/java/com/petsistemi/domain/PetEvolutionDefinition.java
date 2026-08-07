package com.petsistemi.domain;

/**
 * Definition of a pet evolution stage (swapping definition or representation based on level).
 */
public record PetEvolutionDefinition(
        int minLevel,
        String targetDefinitionId,
        String displayNameOverride,
        PetVector3 scaleOverride
) {
}

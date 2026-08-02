package com.petsistemi.definition;

import com.petsistemi.domain.PetDefinition;
import java.util.Collection;
import java.util.Optional;

public interface PetDefinitionRegistry {

    Optional<PetDefinition> find(String definitionId);

    Collection<PetDefinition> getAll();

    void reload();
}

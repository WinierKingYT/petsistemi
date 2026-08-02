package com.petsistemi.definition;

import com.petsistemi.domain.PetDefinition;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class AtomicPetDefinitionRegistry implements PetDefinitionRegistry {

    private volatile Map<String, PetDefinition> registry = new ConcurrentHashMap<>();

    public void publishSnapshot(Map<String, PetDefinition> newSnapshot) {
        if (newSnapshot != null) {
            this.registry = new ConcurrentHashMap<>(newSnapshot);
        }
    }

    @Override
    public Optional<PetDefinition> find(String id) {
        if (id == null) return Optional.empty();
        return Optional.ofNullable(registry.get(id.toLowerCase()));
    }

    @Override
    public Collection<PetDefinition> getAll() {
        return Collections.unmodifiableCollection(registry.values());
    }

    @Override
    public void reload() {}
}

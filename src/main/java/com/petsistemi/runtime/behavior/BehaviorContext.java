package com.petsistemi.runtime.behavior;

import com.petsistemi.domain.PetDefinition;
import org.bukkit.entity.Entity;

import java.util.LinkedHashMap;
import java.util.Map;

/** Runtime data made available to behavior conditions and actions. */
public record BehaviorContext(Entity petEntity, PetDefinition petDefinition, Map<String, Object> attributes) {
    public BehaviorContext {
        attributes = attributes == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(attributes));
    }

    public static BehaviorContext of(Entity petEntity, PetDefinition definition) {
        return new BehaviorContext(petEntity, definition, Map.of());
    }
}

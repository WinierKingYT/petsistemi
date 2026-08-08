package com.petsistemi.domain.behavior;

import org.bukkit.NamespacedKey;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** One condition invocation in a behavior definition. */
public record BehaviorConditionDefinition(NamespacedKey key, Map<String, Object> parameters) {
    public BehaviorConditionDefinition {
        Objects.requireNonNull(key, "condition key null olamaz");
        parameters = parameters == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(parameters));
    }
}

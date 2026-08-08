package com.petsistemi.domain.behavior;

import org.bukkit.NamespacedKey;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** One action invocation in a behavior definition. */
public record BehaviorActionDefinition(NamespacedKey key, Map<String, Object> parameters) {
    public BehaviorActionDefinition {
        Objects.requireNonNull(key, "action key null olamaz");
        parameters = parameters == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(parameters));
    }
}

package com.petsistemi.domain.item;

import org.bukkit.NamespacedKey;

import java.util.LinkedHashMap;
import java.util.Map;

/** One item matcher and the namespaced action it invokes on a pet. */
public record PetItemActionDefinition(
        String id,
        String material,
        Integer customModelData,
        int consumeAmount,
        int cooldownSeconds,
        int minimumLevel,
        int maximumLevel,
        String permission,
        NamespacedKey action,
        Map<String, Object> parameters
) {
    public PetItemActionDefinition {
        parameters = parameters == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(parameters));
    }
}

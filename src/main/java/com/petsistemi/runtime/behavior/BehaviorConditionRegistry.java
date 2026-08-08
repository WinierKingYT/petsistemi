package com.petsistemi.runtime.behavior;

import org.bukkit.NamespacedKey;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class BehaviorConditionRegistry {
    private final Map<NamespacedKey, BehaviorCondition> conditions = new LinkedHashMap<>();

    public void register(NamespacedKey key, BehaviorCondition condition) {
        if (key != null && condition != null) conditions.put(key, condition);
    }

    public BehaviorCondition get(NamespacedKey key) { return key == null ? null : conditions.get(key); }

    public Set<NamespacedKey> supportedKeys() { return Collections.unmodifiableSet(conditions.keySet()); }
}

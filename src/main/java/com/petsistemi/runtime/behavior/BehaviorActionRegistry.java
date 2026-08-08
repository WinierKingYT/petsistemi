package com.petsistemi.runtime.behavior;

import org.bukkit.NamespacedKey;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class BehaviorActionRegistry {
    private final Map<NamespacedKey, BehaviorAction> actions = new LinkedHashMap<>();

    public void register(NamespacedKey key, BehaviorAction action) {
        if (key != null && action != null) actions.put(key, action);
    }

    public BehaviorAction get(NamespacedKey key) { return key == null ? null : actions.get(key); }

    public Set<NamespacedKey> supportedKeys() { return Collections.unmodifiableSet(actions.keySet()); }
}

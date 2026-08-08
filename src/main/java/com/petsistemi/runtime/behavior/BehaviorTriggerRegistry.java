package com.petsistemi.runtime.behavior;

import org.bukkit.NamespacedKey;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** Registry of trigger names that callers are allowed to fire. */
public final class BehaviorTriggerRegistry {
    private final Set<NamespacedKey> triggers = new LinkedHashSet<>();

    public void register(NamespacedKey key) { if (key != null) triggers.add(key); }

    public boolean contains(NamespacedKey key) { return key != null && triggers.contains(key); }

    public Set<NamespacedKey> supportedKeys() { return Collections.unmodifiableSet(triggers); }
}

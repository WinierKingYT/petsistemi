package com.petsistemi.runtime;

import com.petsistemi.domain.RuntimeRepresentationType;

import java.util.Collections;
import org.bukkit.NamespacedKey;

import com.petsistemi.domain.RuntimeKeyResolver;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Registry mapping YAML representation types to their runtime controllers. */
public final class PetRepresentationRegistry {

    private final Map<NamespacedKey, PetRepresentationController> controllers = new LinkedHashMap<>();

    public void register(RuntimeRepresentationType type, PetRepresentationController controller) {
        if (type != null && controller != null) {
            register(RuntimeKeyResolver.representationKey(type), controller);
        }
    }

    public PetRepresentationController get(RuntimeRepresentationType type) {
        return get(RuntimeKeyResolver.representationKey(type));
    }

    public void register(NamespacedKey key, PetRepresentationController controller) {
        if (key != null && controller != null) controllers.put(key, controller);
    }

    public PetRepresentationController get(NamespacedKey key) {
        return controllers.get(key != null ? key : RuntimeKeyResolver.representationKey(RuntimeRepresentationType.ENTITY));
    }

    public void unregister(NamespacedKey key) {
        if (key != null) controllers.remove(key);
    }

    /** Built-in compatibility view. Use {@link #supportedKeys()} for extension-aware callers. */
    public Set<RuntimeRepresentationType> supported() {
        java.util.Set<RuntimeRepresentationType> builtIns = java.util.EnumSet.noneOf(RuntimeRepresentationType.class);
        for (NamespacedKey key : controllers.keySet()) {
            RuntimeRepresentationType type = RuntimeKeyResolver.builtInRepresentation(key);
            if (type != null) builtIns.add(type);
        }
        return Collections.unmodifiableSet(builtIns);
    }

    public Set<NamespacedKey> supportedKeys() {
        return Collections.unmodifiableSet(controllers.keySet());
    }
}

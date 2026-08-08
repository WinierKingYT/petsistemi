package com.petsistemi.runtime;

import com.petsistemi.domain.PetMovementType;

import java.util.Collections;
import org.bukkit.NamespacedKey;

import com.petsistemi.domain.RuntimeKeyResolver;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Registry mapping YAML movement types to their runtime controllers. */
public final class PetMovementRegistry {

    private final Map<NamespacedKey, PetMovementController> controllers = new LinkedHashMap<>();

    public void register(PetMovementType type, PetMovementController controller) {
        if (type != null && controller != null) {
            register(RuntimeKeyResolver.movementKey(type), controller);
        }
    }

    public PetMovementController get(PetMovementType type) {
        return get(RuntimeKeyResolver.movementKey(type));
    }

    public void register(NamespacedKey key, PetMovementController controller) {
        if (key != null && controller != null) controllers.put(key, controller);
    }

    public PetMovementController get(NamespacedKey key) {
        return controllers.get(key != null ? key : RuntimeKeyResolver.movementKey(PetMovementType.GROUND_FOLLOW));
    }

    /** Built-in compatibility view. Use {@link #supportedKeys()} for extension-aware callers. */
    public Set<PetMovementType> supported() {
        java.util.Set<PetMovementType> builtIns = java.util.EnumSet.noneOf(PetMovementType.class);
        for (NamespacedKey key : controllers.keySet()) {
            PetMovementType type = RuntimeKeyResolver.builtInMovement(key);
            if (type != null) builtIns.add(type);
        }
        return Collections.unmodifiableSet(builtIns);
    }

    public Set<NamespacedKey> supportedKeys() {
        return Collections.unmodifiableSet(controllers.keySet());
    }
}

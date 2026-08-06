package com.petsistemi.runtime;

import com.petsistemi.domain.RuntimeRepresentationType;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/** Registry mapping YAML representation types to their runtime controllers. */
public final class PetRepresentationRegistry {

    private final Map<RuntimeRepresentationType, PetRepresentationController> controllers =
            new EnumMap<>(RuntimeRepresentationType.class);

    public void register(RuntimeRepresentationType type, PetRepresentationController controller) {
        if (type != null && controller != null) {
            controllers.put(type, controller);
        }
    }

    public PetRepresentationController get(RuntimeRepresentationType type) {
        return controllers.get(type != null ? type : RuntimeRepresentationType.ENTITY);
    }

    public Set<RuntimeRepresentationType> supported() {
        return Collections.unmodifiableSet(controllers.keySet());
    }
}

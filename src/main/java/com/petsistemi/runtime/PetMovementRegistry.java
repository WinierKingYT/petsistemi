package com.petsistemi.runtime;

import com.petsistemi.domain.PetMovementType;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/** Registry mapping YAML movement types to their runtime controllers. */
public final class PetMovementRegistry {

    private final Map<PetMovementType, PetMovementController> controllers =
            new EnumMap<>(PetMovementType.class);

    public void register(PetMovementType type, PetMovementController controller) {
        if (type != null && controller != null) {
            controllers.put(type, controller);
        }
    }

    public PetMovementController get(PetMovementType type) {
        return controllers.get(type != null ? type : PetMovementType.GROUND_FOLLOW);
    }

    public Set<PetMovementType> supported() {
        return Collections.unmodifiableSet(controllers.keySet());
    }
}

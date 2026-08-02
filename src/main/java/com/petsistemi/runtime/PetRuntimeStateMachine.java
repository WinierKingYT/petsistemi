package com.petsistemi.runtime;

import com.petsistemi.domain.PetRuntimeState;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public class PetRuntimeStateMachine {

    private static final Map<PetRuntimeState, Set<PetRuntimeState>> VALID_TRANSITIONS = Map.of(
            PetRuntimeState.SPAWNING, EnumSet.of(PetRuntimeState.ACTIVE, PetRuntimeState.FAILED, PetRuntimeState.DESPAWNING),
            PetRuntimeState.ACTIVE, EnumSet.of(PetRuntimeState.RESTORING, PetRuntimeState.DESPAWNING, PetRuntimeState.FAILED),
            PetRuntimeState.RESTORING, EnumSet.of(PetRuntimeState.ACTIVE, PetRuntimeState.FAILED, PetRuntimeState.DESPAWNING),
            PetRuntimeState.DESPAWNING, EnumSet.of(PetRuntimeState.FAILED),
            PetRuntimeState.FAILED, EnumSet.of(PetRuntimeState.SPAWNING, PetRuntimeState.RESTORING, PetRuntimeState.DESPAWNING)
    );

    public static boolean canTransition(PetRuntimeState current, PetRuntimeState target) {
        if (current == target) return true;
        Set<PetRuntimeState> allowed = VALID_TRANSITIONS.get(current);
        return allowed != null && allowed.contains(target);
    }
}

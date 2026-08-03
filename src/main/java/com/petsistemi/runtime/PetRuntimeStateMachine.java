package com.petsistemi.runtime;

import com.petsistemi.domain.PetRuntimeState;

public class PetRuntimeStateMachine {

    public static boolean canTransition(PetRuntimeState current, PetRuntimeState target) {
        if (current == null) return false;
        return current.canTransitionTo(target);
    }
}

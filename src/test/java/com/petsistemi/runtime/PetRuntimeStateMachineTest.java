package com.petsistemi.runtime;

import com.petsistemi.domain.PetRuntimeState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PetRuntimeStateMachineTest {

    @Test
    void testValidTransitionsAllowed() {
        assertTrue(PetRuntimeStateMachine.canTransition(PetRuntimeState.SPAWNING, PetRuntimeState.ACTIVE));
        assertTrue(PetRuntimeStateMachine.canTransition(PetRuntimeState.ACTIVE, PetRuntimeState.RESTORING));
        assertTrue(PetRuntimeStateMachine.canTransition(PetRuntimeState.RESTORING, PetRuntimeState.ACTIVE));
        assertTrue(PetRuntimeStateMachine.canTransition(PetRuntimeState.ACTIVE, PetRuntimeState.DESPAWNING));
    }

    @Test
    void testSelfTransitionAllowed() {
        assertTrue(PetRuntimeStateMachine.canTransition(PetRuntimeState.ACTIVE, PetRuntimeState.ACTIVE));
    }
}

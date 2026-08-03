package com.petsistemi.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PetRuntimeStateTest {

    @Test
    void testValidStateTransitions() {
        assertTrue(PetRuntimeState.ABSENT.canTransitionTo(PetRuntimeState.SPAWNING));
        assertTrue(PetRuntimeState.SPAWNING.canTransitionTo(PetRuntimeState.ACTIVE));
        assertTrue(PetRuntimeState.ACTIVE.canTransitionTo(PetRuntimeState.DESPAWNING));
        assertTrue(PetRuntimeState.DESPAWNING.canTransitionTo(PetRuntimeState.ABSENT));
        assertTrue(PetRuntimeState.ACTIVE.canTransitionTo(PetRuntimeState.RESTORING));
        assertTrue(PetRuntimeState.RESTORING.canTransitionTo(PetRuntimeState.ACTIVE));
    }

    @Test
    void testInvalidStateTransitions() {
        assertFalse(PetRuntimeState.ABSENT.canTransitionTo(PetRuntimeState.ACTIVE));
        assertFalse(PetRuntimeState.DESPAWNING.canTransitionTo(PetRuntimeState.ACTIVE));
    }
}

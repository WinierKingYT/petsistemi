package com.petsistemi.runtime;

import com.petsistemi.domain.PetMovementDefinition;
import com.petsistemi.domain.PetMovementType;
import com.petsistemi.domain.PetRuntimeState;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The runaway guard is a last-resort snap-back. A fixed threshold would silently cap any
 * definition that deliberately configures a longer leash than the default.
 */
class RunawayGuardTest {

    private static ActivePet petWithTeleportDistance(double teleportDistance) {
        ActivePet pet = new ActivePet(UUID.randomUUID(), UUID.randomUUID(), "wolf", 1,
                UUID.randomUUID(), null, PetRuntimeState.ACTIVE);
        pet.setMovementDefinition(new PetMovementDefinition(PetMovementType.GROUND_FOLLOW,
                0.0, teleportDistance, 0, 0.0, 0.0, 0.0, null));
        return pet;
    }

    @Test
    void defaultAppliesWhenTheDefinitionSetsNoTeleportDistance() {
        assertEquals(PetRuntimeCoordinator.DEFAULT_RUNAWAY_DISTANCE,
                PetRuntimeCoordinator.runawayDistance(petWithTeleportDistance(0.0)), 1e-9);
    }

    @Test
    void shorterTeleportDistancesDoNotTightenTheGuard() {
        // Movement controllers teleport at 24 themselves; the guard stays the safety net.
        assertEquals(PetRuntimeCoordinator.DEFAULT_RUNAWAY_DISTANCE,
                PetRuntimeCoordinator.runawayDistance(petWithTeleportDistance(24.0)), 1e-9);
    }

    @Test
    void longerTeleportDistanceRaisesTheGuardInsteadOfBeingCapped() {
        assertEquals(120.0, PetRuntimeCoordinator.runawayDistance(petWithTeleportDistance(120.0)), 1e-9);
    }

    @Test
    void missingMovementDefinitionFallsBackToTheDefault() {
        ActivePet pet = new ActivePet(UUID.randomUUID(), UUID.randomUUID(), "wolf", 1,
                UUID.randomUUID(), null, PetRuntimeState.ACTIVE);

        assertEquals(PetRuntimeCoordinator.DEFAULT_RUNAWAY_DISTANCE,
                PetRuntimeCoordinator.runawayDistance(pet), 1e-9);
        assertEquals(PetRuntimeCoordinator.DEFAULT_RUNAWAY_DISTANCE,
                PetRuntimeCoordinator.runawayDistance(null), 1e-9);
    }
}

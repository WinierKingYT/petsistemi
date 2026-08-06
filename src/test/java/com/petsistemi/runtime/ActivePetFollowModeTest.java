package com.petsistemi.runtime;

import com.petsistemi.domain.PetFollowMode;
import com.petsistemi.domain.PetRuntimeState;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ActivePetFollowModeTest {

    @Test
    void defaultsToFollow() {
        ActivePet pet = new ActivePet(UUID.randomUUID(), UUID.randomUUID(), "wolf", 1, null, null, PetRuntimeState.ACTIVE);
        assertEquals(PetFollowMode.FOLLOW, pet.getFollowMode());
    }

    @Test
    void storesAndOverridesMode() {
        ActivePet pet = new ActivePet(UUID.randomUUID(), UUID.randomUUID(), "wolf", 1, null, null, PetRuntimeState.ACTIVE);
        pet.setFollowMode(PetFollowMode.STAY);
        assertEquals(PetFollowMode.STAY, pet.getFollowMode());
        pet.setFollowMode(PetFollowMode.WANDER);
        assertEquals(PetFollowMode.WANDER, pet.getFollowMode());
    }

    @Test
    void nullModeFallsBackToFollow() {
        ActivePet pet = new ActivePet(UUID.randomUUID(), UUID.randomUUID(), "wolf", 1, null, null, PetRuntimeState.ACTIVE);
        pet.setFollowMode(null);
        assertEquals(PetFollowMode.FOLLOW, pet.getFollowMode());
    }
}

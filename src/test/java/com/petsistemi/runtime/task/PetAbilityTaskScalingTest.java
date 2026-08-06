package com.petsistemi.runtime.task;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PetAbilityTaskScalingTest {

    @Test
    void lowLevelsGetBaseAmplifier() {
        assertEquals(0, PetAbilityTask.buffAmplifier(1));
        assertEquals(0, PetAbilityTask.buffAmplifier(5));
    }

    @Test
    void amplifierGrowsEveryFiveLevels() {
        assertEquals(1, PetAbilityTask.buffAmplifier(6));
        assertEquals(1, PetAbilityTask.buffAmplifier(10));
        assertEquals(2, PetAbilityTask.buffAmplifier(11));
        assertEquals(2, PetAbilityTask.buffAmplifier(15));
        assertEquals(3, PetAbilityTask.buffAmplifier(16));
    }

    @Test
    void amplifierIsCappedAtSafeValue() {
        assertEquals(3, PetAbilityTask.buffAmplifier(100));
    }

    @Test
    void zeroOrNegativeLevelsAreClamped() {
        assertEquals(0, PetAbilityTask.buffAmplifier(0));
        assertEquals(0, PetAbilityTask.buffAmplifier(-5));
    }
}

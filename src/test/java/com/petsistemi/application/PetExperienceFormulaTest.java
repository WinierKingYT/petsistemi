package com.petsistemi.application;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PetExperienceFormulaTest {

    @Test
    void testRequiredExperienceForLevel() {
        DefaultPetExperienceService service = new DefaultPetExperienceService(null, null, null, null, null);

        assertEquals(0, service.requiredExperienceForLevel(1));
        assertEquals(100, service.requiredExperienceForLevel(2));
        assertEquals(400, service.requiredExperienceForLevel(3));
        assertEquals(900, service.requiredExperienceForLevel(4));
        assertEquals(8100, service.requiredExperienceForLevel(10));
        assertEquals(10000, service.requiredExperienceForLevel(11));
    }

    @Test
    void testCalculateLevelFromXp() {
        DefaultPetExperienceService service = new DefaultPetExperienceService(null, null, null, null, null);

        assertEquals(1, service.calculateLevelFromXp(0));
        assertEquals(1, service.calculateLevelFromXp(99));
        assertEquals(2, service.calculateLevelFromXp(100));
        assertEquals(2, service.calculateLevelFromXp(399));
        assertEquals(3, service.calculateLevelFromXp(400));
        assertEquals(4, service.calculateLevelFromXp(900));
        assertEquals(11, service.calculateLevelFromXp(10000));
    }
}

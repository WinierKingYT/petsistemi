package com.petsistemi.application;

import com.petsistemi.progression.ExponentialExperienceCurve;
import com.petsistemi.progression.LinearExperienceCurve;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PetExperienceFormulaTest {

    @Test
    void testLinearExperienceCurveFormula() {
        DefaultPetExperienceService service = new DefaultPetExperienceService(null, null, null, null, null, new LinearExperienceCurve(100));

        assertEquals(0, service.requiredExperienceForLevel(1));
        assertEquals(100, service.requiredExperienceForLevel(2));
        assertEquals(200, service.requiredExperienceForLevel(3));
        assertEquals(900, service.requiredExperienceForLevel(10));
    }

    @Test
    void testCalculateLevelFromLinearXp() {
        DefaultPetExperienceService service = new DefaultPetExperienceService(null, null, null, null, null, new LinearExperienceCurve(100));

        assertEquals(1, service.calculateLevelFromXp(0));
        assertEquals(1, service.calculateLevelFromXp(99));
        assertEquals(2, service.calculateLevelFromXp(100));
        assertEquals(3, service.calculateLevelFromXp(200));
    }
}

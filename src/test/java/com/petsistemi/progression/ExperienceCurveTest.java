package com.petsistemi.progression;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ExperienceCurveTest {

    @Test
    void testLinearExperienceCurve() {
        ExperienceCurve curve = new LinearExperienceCurve(100);
        assertEquals(0, curve.getRequiredExperience(1));
        assertEquals(100, curve.getRequiredExperience(2));
        assertEquals(900, curve.getRequiredExperience(10));

        assertEquals(1, curve.getLevelForExperience(0));
        assertEquals(2, curve.getLevelForExperience(150));
    }

    @Test
    void testTableExperienceCurve() {
        ExperienceCurve curve = new TableExperienceCurve(List.of(0L, 100L, 300L, 600L, 1000L));
        assertEquals(0, curve.getRequiredExperience(1));
        assertEquals(100, curve.getRequiredExperience(2));
        assertEquals(600, curve.getRequiredExperience(4));

        assertEquals(1, curve.getLevelForExperience(50));
        assertEquals(3, curve.getLevelForExperience(400));
    }
}

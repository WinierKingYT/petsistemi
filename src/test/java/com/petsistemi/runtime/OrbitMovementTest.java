package com.petsistemi.runtime;

import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OrbitMovementTest {

    @Test
    void nextAngleAdvancesClockwiseBySpeedPerGameTick() {
        double next = OrbitMovement.nextAngle(0.0, 1.2, 5, true);
        assertEquals(1.2 * (5.0 / 20.0), next, 1e-9);
    }

    @Test
    void nextAngleReversesWhenCounterClockwise() {
        double next = OrbitMovement.nextAngle(0.0, 1.2, 5, false);
        assertEquals(-(1.2 * (5.0 / 20.0)), next, 1e-9);
    }

    @Test
    void nextAngleWrapsAroundTwoPi() {
        double next = OrbitMovement.nextAngle(Math.PI, Math.PI, 20, true);
        double expected = Math.PI + Math.PI * (20.0 / 20.0);
        assertEquals(expected % (2.0 * Math.PI), next, 1e-9);
        assertTrue(next >= 0.0 && next < 2.0 * Math.PI);
    }

    @Test
    void orbitPositionKeepsRadiusAndHeight() {
        Location center = new Location(null, 100.0, 64.0, 200.0);
        Location pos = OrbitMovement.orbitPosition(center, 0.785398, 1.7, 1.4);

        assertEquals(100.0, center.getX(), 1e-9);
        assertEquals(64.0, center.getY(), 1e-9);
        assertEquals(200.0, center.getZ(), 1e-9);

        double dx = pos.getX() - center.getX();
        double dy = pos.getY() - center.getY();
        double dz = pos.getZ() - center.getZ();
        assertEquals(1.7, Math.sqrt(dx * dx + dz * dz), 1e-6, "horizontal distance must equal radius");
        assertEquals(1.4, dy, 1e-6, "height must be constant");
    }

    @Test
    void orbitPositionMatchesCircleParametricFormula() {
        Location center = new Location(null, 0.0, 0.0, 0.0);
        double angle = 0.9;
        Location pos = OrbitMovement.orbitPosition(center, angle, 2.0, 0.5);

        assertEquals(Math.cos(angle) * 2.0, pos.getX(), 1e-6);
        assertEquals(Math.sin(angle) * 2.0, pos.getZ(), 1e-6);
        assertEquals(0.5, pos.getY(), 1e-6);
    }
}

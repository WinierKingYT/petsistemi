package com.petsistemi.runtime;

import org.bukkit.Location;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HoverMovementTest {

    private static final double BOB_AMPLITUDE = 0.12;

    @Test
    void hoverPointSitsDirectlyAboveTheOwner() {
        Location owner = new Location(null, 10.0, 64.0, 20.0);

        Location pos = HoverMovement.targetPosition(owner, 2.2, 0.0);

        assertEquals(10.0, pos.getX(), 1e-9, "yatayda kaymamalı");
        assertEquals(20.0, pos.getZ(), 1e-9, "yatayda kaymamalı");
    }

    @Test
    void phaseZeroGivesExactlyTheConfiguredHeight() {
        Location owner = new Location(null, 0.0, 64.0, 0.0);

        assertEquals(66.2, HoverMovement.targetPosition(owner, 2.2, 0.0).getY(), 1e-9);
    }

    /** The bob is decoration: it must never drift the pet away from its configured height. */
    @Test
    void bobStaysWithinAmplitudeForEveryPhase() {
        Location owner = new Location(null, 0.0, 64.0, 0.0);
        double height = 2.0;

        for (int i = 0; i < 64; i++) {
            double phase = i * (Math.PI / 8.0);
            double y = HoverMovement.targetPosition(owner, height, phase).getY();
            double offset = y - (64.0 + height);
            assertTrue(Math.abs(offset) <= BOB_AMPLITUDE + 1e-9,
                    () -> "faz " + phase + " genliği aştı: " + offset);
        }
    }

    @Test
    void quarterPhaseReachesTheTopOfTheBob() {
        Location owner = new Location(null, 0.0, 64.0, 0.0);

        double y = HoverMovement.targetPosition(owner, 2.0, Math.PI / 2.0).getY();

        assertEquals(64.0 + 2.0 + BOB_AMPLITUDE, y, 1e-9);
    }

    @Test
    void ownerYawDoesNotAffectTheHoverPoint() {
        Location facingSouth = new Location(null, 5.0, 64.0, 5.0);
        facingSouth.setYaw(0.0f);
        Location facingWest = new Location(null, 5.0, 64.0, 5.0);
        facingWest.setYaw(90.0f);

        Location a = HoverMovement.targetPosition(facingSouth, 2.0, 0.3);
        Location b = HoverMovement.targetPosition(facingWest, 2.0, 0.3);

        assertEquals(a.getX(), b.getX(), 1e-9);
        assertEquals(a.getZ(), b.getZ(), 1e-9);
        assertEquals(a.getY(), b.getY(), 1e-9);
    }
}

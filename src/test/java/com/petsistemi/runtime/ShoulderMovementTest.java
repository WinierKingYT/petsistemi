package com.petsistemi.runtime;

import org.bukkit.Location;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShoulderMovementTest {

    /** Minecraft yaw 0 faces +Z, so "forward" must land on +Z. */
    @Test
    void forwardFollowsOwnerFacingAtYawZero() {
        Location owner = new Location(null, 0.0, 64.0, 0.0);
        owner.setYaw(0.0f);

        Location pos = ShoulderMovement.targetPosition(owner, 1.0, 0.9);

        assertEquals(0.0, pos.getX(), 1e-9);
        assertEquals(1.0, pos.getZ(), 1e-9, "yaw 0'da ileri +Z olmalı");
        assertEquals(64.9, pos.getY(), 1e-9);
    }

    @Test
    void forwardTracksYawQuarterTurn() {
        Location owner = new Location(null, 0.0, 64.0, 0.0);
        owner.setYaw(-90.0f); // facing +X

        Location pos = ShoulderMovement.targetPosition(owner, 2.0, 0.0);

        assertEquals(2.0, pos.getX(), 1e-9, "yaw -90'da ileri +X olmalı");
        assertEquals(0.0, pos.getZ(), 1e-9);
    }

    @Test
    void heightIsAppliedIndependentlyOfFacing() {
        for (float yaw : new float[]{0.0f, 45.0f, -90.0f, 180.0f, 270.0f}) {
            Location owner = new Location(null, 3.0, 64.0, -7.0);
            owner.setYaw(yaw);

            assertEquals(64.9, ShoulderMovement.targetPosition(owner, 0.35, 0.9).getY(), 1e-9,
                    "yükseklik yaw'dan bağımsız olmalı (yaw=" + yaw + ")");
        }
    }

    /** The offset is a fixed radius around the owner regardless of which way they face. */
    @Test
    void horizontalDistanceEqualsForwardForEveryYaw() {
        double forward = 1.4;
        for (int deg = 0; deg < 360; deg += 15) {
            Location owner = new Location(null, 0.0, 64.0, 0.0);
            owner.setYaw(deg);

            Location pos = ShoulderMovement.targetPosition(owner, forward, 0.9);
            double horizontal = Math.hypot(pos.getX(), pos.getZ());

            assertEquals(forward, horizontal, 1e-9, "yaw=" + deg);
        }
    }

    @Test
    void oppositeYawsPlaceThePetOnOppositeSides() {
        Location north = new Location(null, 0.0, 64.0, 0.0);
        north.setYaw(0.0f);
        Location south = new Location(null, 0.0, 64.0, 0.0);
        south.setYaw(180.0f);

        Location a = ShoulderMovement.targetPosition(north, 1.0, 0.0);
        Location b = ShoulderMovement.targetPosition(south, 1.0, 0.0);

        assertEquals(-a.getZ(), b.getZ(), 1e-9);
        assertTrue(Math.abs(a.getZ() - b.getZ()) > 1e-6, "180 derece dönüş konumu değiştirmeli");
    }
}

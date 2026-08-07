package com.petsistemi.runtime;

import org.bukkit.Location;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FormationMovementTest {

    @Test
    void slotZeroSitsBesideTheOwnerAtTheGivenRadius() {
        Location owner = new Location(null, 0.0, 64.0, 0.0);
        owner.setYaw(0.0f);

        Location pos = FormationMovement.slotPosition(owner, 0, 4, 0.9, 0.4);

        assertEquals(0.9, Math.hypot(pos.getX(), pos.getZ()), 1e-9, "yarıçap korunmalı");
        assertEquals(64.4, pos.getY(), 1e-9);
    }

    @Test
    void childSlotsSpreadEvenlyAroundTheOwner() {
        Location owner = new Location(null, 0.0, 64.0, 0.0);
        owner.setYaw(0.0f);
        int totalSlots = 4; // 1 primary + 3 children

        double[] angles = new double[3];
        for (int slot = 1; slot <= 3; slot++) {
            Location pos = FormationMovement.slotPosition(owner, slot, totalSlots, 1.3, 0.6);
            assertEquals(1.3, Math.hypot(pos.getX(), pos.getZ()), 1e-9, "slot " + slot + " yarıçapı");
            angles[slot - 1] = Math.atan2(pos.getZ(), pos.getX());
        }

        // Three children on a circle must be 120 degrees apart.
        double step = 2.0 * Math.PI / 3.0;
        assertEquals(step, normalize(angles[1] - angles[0]), 1e-9);
        assertEquals(step, normalize(angles[2] - angles[1]), 1e-9);
    }

    @Test
    void allSlotsShareTheConfiguredHeight() {
        Location owner = new Location(null, 12.0, 70.0, -3.0);
        owner.setYaw(37.0f);

        for (int slot = 1; slot <= 3; slot++) {
            assertEquals(70.6, FormationMovement.slotPosition(owner, slot, 4, 1.3, 0.6).getY(), 1e-9);
        }
    }

    /** The formation turns with the owner, so relative geometry must survive a yaw change. */
    @Test
    void rotatingTheOwnerRotatesTheWholeFormation() {
        Location facingSouth = new Location(null, 0.0, 64.0, 0.0);
        facingSouth.setYaw(0.0f);
        Location facingWest = new Location(null, 0.0, 64.0, 0.0);
        facingWest.setYaw(90.0f);

        Location a = FormationMovement.slotPosition(facingSouth, 1, 4, 1.3, 0.6);
        Location b = FormationMovement.slotPosition(facingWest, 1, 4, 1.3, 0.6);

        double delta = normalize(Math.atan2(b.getZ(), b.getX()) - Math.atan2(a.getZ(), a.getX()));
        assertEquals(Math.toRadians(90.0), delta, 1e-9, "formasyon sahiple birlikte dönmeli");
    }

    /** totalSlots == 1 means "primary only"; the divisor guard must not blow up. */
    @Test
    void singleSlotFormationDoesNotDivideByZero() {
        Location owner = new Location(null, 0.0, 64.0, 0.0);
        owner.setYaw(0.0f);

        Location pos = FormationMovement.slotPosition(owner, 1, 1, 1.3, 0.6);

        assertTrue(Double.isFinite(pos.getX()) && Double.isFinite(pos.getZ()),
                "tek slotlu formasyon NaN üretmemeli");
    }

    @Test
    void childrenAreOffsetFromThePrimarySlot() {
        Location owner = new Location(null, 0.0, 64.0, 0.0);
        owner.setYaw(0.0f);

        Location primary = FormationMovement.slotPosition(owner, 0, 4, 0.9, 0.4);
        Location child = FormationMovement.slotPosition(owner, 1, 4, 1.3, 0.6);

        // Location.distance() needs a non-null world, so compare the raw coordinates.
        double gap = Math.hypot(primary.getX() - child.getX(), primary.getZ() - child.getZ());
        assertTrue(gap > 0.1, "birincil ve çocuk aynı noktada olmamalı");
    }

    /** Wraps an angle difference into (-pi, pi] so comparisons don't trip over the 2pi seam. */
    private static double normalize(double radians) {
        double a = radians % (2.0 * Math.PI);
        if (a <= -Math.PI) a += 2.0 * Math.PI;
        if (a > Math.PI) a -= 2.0 * Math.PI;
        return a;
    }
}

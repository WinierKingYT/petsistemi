package com.petsistemi.runtime;

import org.bukkit.Location;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Deque;

import static org.junit.jupiter.api.Assertions.*;

class MovementMathTest {

    @Test
    void hoverPositionBobsAroundHeight() {
        Location owner = new Location(null, 5.0, 64.0, 5.0);
        Location p0 = HoverMovement.targetPosition(owner, 2.2, 0.0);
        Location p1 = HoverMovement.targetPosition(owner, 2.2, Math.PI / 2.0);

        assertEquals(5.0, p0.getX(), 1e-9);
        assertEquals(5.0, p0.getZ(), 1e-9);
        double bob0 = p0.getY() - 64.0;
        assertTrue(bob0 >= 2.2 - 0.12 - 1e-9 && bob0 <= 2.2 + 0.12 + 1e-9,
                "hover height must stay within the bob band");
        assertNotEquals(p0.getY(), p1.getY(), "bob must change with phase");
    }

    @Test
    void shoulderTargetIsForwardAndUp() {
        // Facing +Z (yaw=0): forward is +Z in Minecraft coordinates
        Location owner = new Location(null, 0.0, 64.0, 0.0);
        owner.setYaw(0.0f);

        Location target = ShoulderMovement.targetPosition(owner, 0.35, 0.9);

        assertEquals(0.35, target.getZ(), 1e-6, "forward offset along facing");
        assertEquals(0.0, target.getX(), 1e-6);
        assertEquals(64.9, target.getY(), 1e-6);
    }

    @Test
    void trailQueueTrimsToTrailLength() {
        Deque<Location> trail = new ArrayDeque<>();
        org.bukkit.World world = org.mockito.Mockito.mock(org.bukkit.World.class);
        Location start = new Location(world, 0.0, 64.0, 0.0);

        // Simulate the owner walking along +X, one block per step
        for (int i = 0; i <= 20; i++) {
            TrailMovement.pushOwnerPosition(trail, start.clone().add(i, 0.0, 0.0), 0.5, 6.0);
        }

        assertFalse(trail.isEmpty());
        assertEquals(12, trail.size(), "max size = ceil(6.0/0.5)");
        // The deque keeps the last 12 points: the front is ~6 blocks behind the owner (x=20)
        assertEquals(9.0, trail.peekFirst().getX(), 1e-6, "front point trails the owner by trail length");
        assertEquals(20.0, trail.peekLast().getX(), 1e-6, "last element is the newest position");
    }

    @Test
    void trailQueueDropsPointsCloserThanSpacing() {
        Deque<Location> trail = new ArrayDeque<>();
        org.bukkit.World world = org.mockito.Mockito.mock(org.bukkit.World.class);
        Location start = new Location(world, 0.0, 64.0, 0.0);
        TrailMovement.pushOwnerPosition(trail, start.clone(), 0.5, 6.0);
        TrailMovement.pushOwnerPosition(trail, start.clone().add(0.1, 0.0, 0.0), 0.5, 6.0);

        assertEquals(1, trail.size(), "points within spacing must be merged");
    }

    @Test
    void formationSlotZeroIsBesideOwner() {
        // Facing +X (yaw=-90): right = +Z
        Location owner = new Location(null, 0.0, 64.0, 0.0);
        owner.setYaw(-90.0f);

        Location slot = FormationMovement.slotPosition(owner, 0, 4, 0.9, 0.4);

        assertEquals(0.0, slot.getX(), 1e-6);
        assertEquals(0.9, slot.getZ(), 1e-6);
        assertEquals(64.4, slot.getY(), 1e-6);
    }

    @Test
    void formationChildrenSpreadEvenlyAroundOwner() {
        Location owner = new Location(null, 0.0, 64.0, 0.0);
        owner.setYaw(0.0f);

        int total = 4; // primary + 3 children
        double[] xs = new double[total - 1];
        double[] zs = new double[total - 1];
        for (int slot = 1; slot < total; slot++) {
            Location pos = FormationMovement.slotPosition(owner, slot, total, 1.3, 0.6);
            xs[slot - 1] = pos.getX();
            zs[slot - 1] = pos.getZ();
        }

        for (int i = 0; i < xs.length; i++) {
            assertEquals(1.3, Math.sqrt(xs[i] * xs[i] + zs[i] * zs[i]), 1e-6, "child radius");
        }
        assertNotEquals(xs[0], xs[1], 1e-9);
        assertNotEquals(zs[0], zs[1], 1e-9);
    }

    @Test
    void formationRotatesWithOwner() {
        Location ownerA = new Location(null, 0.0, 64.0, 0.0);
        ownerA.setYaw(0.0f);
        Location ownerB = new Location(null, 0.0, 64.0, 0.0);
        ownerB.setYaw(90.0f);

        Location slotA = FormationMovement.slotPosition(ownerA, 1, 3, 1.3, 0.6);
        Location slotB = FormationMovement.slotPosition(ownerB, 1, 3, 1.3, 0.6);

        double distA = Math.hypot(slotA.getX(), slotA.getZ());
        double distB = Math.hypot(slotB.getX(), slotB.getZ());
        assertEquals(1.3, distA, 1e-6);
        assertEquals(1.3, distB, 1e-6);
        assertNotEquals(slotA.getX(), slotB.getX(), 1e-9);
        assertNotEquals(slotA.getZ(), slotB.getZ(), 1e-9);
    }
}

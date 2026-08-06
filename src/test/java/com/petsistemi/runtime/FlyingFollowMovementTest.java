package com.petsistemi.runtime;

import org.bukkit.Location;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FlyingFollowMovementTest {

    @Test
    void targetLocationOffsetsToOwnersRightSideAtGivenHeight() {
        // Facing +X (yaw = -90). Right of the owner is +Z.
        Location owner = new Location(null, 10.0, 64.0, 20.0);
        owner.setYaw(-90.0f);

        Location target = FlyingFollowMovement.targetLocation(owner, 1.1, 1.5);

        double dx = target.getX() - owner.getX();
        double dy = target.getY() - owner.getY();
        double dz = target.getZ() - owner.getZ();
        assertEquals(1.5, dy, 1e-6, "height offset");
        assertEquals(0.0, dx, 1e-6, "no forward offset");
        assertEquals(1.1, dz, 1e-6, "right-side offset");
    }

    @Test
    void targetLocationKeepsDistanceFromOwner() {
        Location owner = new Location(null, 0.0, 64.0, 0.0);
        owner.setYaw(30.0f);

        Location target = FlyingFollowMovement.targetLocation(owner, 1.1, 1.5);

        double dx = target.getX() - owner.getX();
        double dz = target.getZ() - owner.getZ();
        assertEquals(1.1, Math.sqrt(dx * dx + dz * dz), 1e-6);
    }
}

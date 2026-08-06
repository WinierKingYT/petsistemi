package com.petsistemi.runtime;

import org.bukkit.Location;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MirrorMovementTest {

    @Test
    void targetLocationOffsetsToFrontOfOwner() {
        // Facing +X (yaw = -90). Front of the owner is +X, but MirrorMovement places
        // the pet at (dx = -sin(yaw), dz = cos(yaw)), so for yaw = -90:
        // dx = -sin(-90°) = -(-1) = +1, dz = cos(-90°) = 0.
        Location owner = new Location(null, 10.0, 64.0, 20.0);
        owner.setYaw(-90.0f);

        Location target = MirrorMovement.targetLocation(owner, 1.5, 0.0);

        assertEquals(11.5, target.getX(), 1e-6, "should be 1.5 blocks right of owner");
        assertEquals(64.0, target.getY(), 1e-6);
        assertEquals(20.0, target.getZ(), 1e-6);
    }

    @Test
    void targetLocationKeepsCorrectDistanceFromOwner() {
        Location owner = new Location(null, 0.0, 64.0, 0.0);
        owner.setYaw(30.0f);

        Location target = MirrorMovement.targetLocation(owner, 1.2, 0.0);

        double dx = target.getX() - owner.getX();
        double dz = target.getZ() - owner.getZ();
        assertEquals(1.2, Math.sqrt(dx * dx + dz * dz), 1e-6, "horizontal distance");
    }

    @Test
    void ownerPoseRecordAccessors() {
        MirrorMovement.OwnerPose pose = new MirrorMovement.OwnerPose(45.0f, 30.0f, true, false, true);

        assertEquals(45.0f, pose.yaw(), 1e-6);
        assertEquals(30.0f, pose.pitch(), 1e-6);
        assertTrue(pose.sneaking());
        assertFalse(pose.gliding());
        assertTrue(pose.jumping());
    }
}
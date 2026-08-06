package com.petsistemi.runtime;

import com.petsistemi.domain.PetAnchorDefinition;
import com.petsistemi.domain.PetAnchorPosition;
import org.bukkit.Location;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AnchoredMovementTest {

    @Test
    void aboveHeadStaysDirectlyAboveOwner() {
        Location owner = new Location(null, 10.0, 64.0, 20.0);
        owner.setYaw(0.0f);

        Location pos = AnchoredMovement.anchorPosition(owner,
                new PetAnchorDefinition(PetAnchorPosition.ABOVE_HEAD, 1.8, 2.5, true));

        assertEquals(10.0, pos.getX(), 1e-6);
        assertEquals(66.5, pos.getY(), 1e-6);
        assertEquals(20.0, pos.getZ(), 1e-6);
    }

    @Test
    void behindRightIsBehindAndRightOfOwner() {
        // Facing +X (yaw=-90): forward +X, right +Z
        Location owner = new Location(null, 0.0, 64.0, 0.0);
        owner.setYaw(-90.0f);

        Location pos = AnchoredMovement.anchorPosition(owner,
                new PetAnchorDefinition(PetAnchorPosition.BEHIND_RIGHT, 2.0, 0.4, true));

        assertTrue(pos.getX() < 0.0, "must be behind the owner");
        assertTrue(pos.getZ() > 0.0, "must be on the owner's right");
        assertEquals(64.4, pos.getY(), 1e-6, "height offset");
    }

    @Test
    void behindLeftMirrorsRight() {
        Location owner = new Location(null, 0.0, 64.0, 0.0);
        owner.setYaw(-90.0f);

        Location right = AnchoredMovement.anchorPosition(owner,
                new PetAnchorDefinition(PetAnchorPosition.BEHIND_RIGHT, 2.0, 0.4, true));
        Location left = AnchoredMovement.anchorPosition(owner,
                new PetAnchorDefinition(PetAnchorPosition.BEHIND_LEFT, 2.0, 0.4, true));

        assertEquals(right.getX(), left.getX(), 1e-6, "same backward distance");
        assertEquals(-right.getZ(), left.getZ(), 1e-6, "mirrored sideways");
    }

    @Test
    void rotateWithOwnerFalseIgnoresYaw() {
        Location ownerA = new Location(null, 0.0, 64.0, 0.0);
        ownerA.setYaw(0.0f);
        Location ownerB = new Location(null, 0.0, 64.0, 0.0);
        ownerB.setYaw(135.0f);

        Location posA = AnchoredMovement.anchorPosition(ownerA,
                new PetAnchorDefinition(PetAnchorPosition.BEHIND_RIGHT, 2.0, 0.4, false));
        Location posB = AnchoredMovement.anchorPosition(ownerB,
                new PetAnchorDefinition(PetAnchorPosition.BEHIND_RIGHT, 2.0, 0.4, false));

        assertEquals(posA.getX(), posB.getX(), 1e-9);
        assertEquals(posA.getZ(), posB.getZ(), 1e-9);
    }
}

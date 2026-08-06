package com.petsistemi.runtime;

import com.petsistemi.domain.PetMovementDefinition;
import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RoamNearOwnerMovementTest {

    @Test
    void randomTargetLiesWithinRadius() {
        RoamNearOwnerMovement movement = new RoamNearOwnerMovement(new Random(42));
        World world = mock(World.class);
        Location ownerLoc = new Location(world, 0.0, 64.0, 0.0);

        for (int i = 0; i < 50; i++) {
            Location target = movement.randomTarget(ownerLoc, 5.0);
            double dist = target.distance(ownerLoc);
            assertTrue(dist <= 5.0 + 1e-6, "target outside radius: " + dist);
        }
    }

    @Test
    void randomTargetWithNullWorldReturnsOwnerLocClone() {
        RoamNearOwnerMovement movement = new RoamNearOwnerMovement(new Random(42));
        Location ownerLoc = new Location(null, 10.0, 64.0, 20.0);

        Location target = movement.randomTarget(ownerLoc, 5.0);

        assertEquals(10.0, target.getX(), 1e-6);
        assertEquals(64.0, target.getY(), 1e-6);
        assertEquals(20.0, target.getZ(), 1e-6);
    }
}
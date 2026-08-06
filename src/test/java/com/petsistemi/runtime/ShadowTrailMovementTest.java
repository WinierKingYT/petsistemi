package com.petsistemi.runtime;

import com.petsistemi.domain.PetMovementDefinition;
import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ShadowTrailMovementTest {

    @Test
    void projectSnapsToHighestBlockY() {
        World world = mock(World.class);
        when(world.getHighestBlockYAt(10, 20)).thenReturn(50);
        Location loc = new Location(world, 10.5, 100.0, 20.5);

        Location projected = ShadowTrailMovement.project(loc);

        assertEquals(10.5, projected.getX(), 1e-6);
        assertEquals(50.05, projected.getY(), 1e-6);
        assertEquals(20.5, projected.getZ(), 1e-6);
    }

    @Test
    void projectWithoutWorldReturnsClone() {
        Location loc = new Location(null, 10.0, 64.0, 20.0);

        Location projected = ShadowTrailMovement.project(loc);

        assertEquals(loc.getX(), projected.getX(), 1e-6);
        assertEquals(loc.getY(), projected.getY(), 1e-6);
        assertEquals(loc.getZ(), projected.getZ(), 1e-6);
    }
}
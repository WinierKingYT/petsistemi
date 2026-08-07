package com.petsistemi.runtime;

import com.petsistemi.domain.PetFollowMode;
import com.petsistemi.domain.PetMovementDefinition;
import com.petsistemi.domain.PetMovementType;
import com.petsistemi.domain.PetRuntimeState;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TELEPORT_ONLY / STATIC_NEAR_OWNER: the pet holds its ground and only snaps to the
 * owner once they walk out of range.
 */
class TeleportOnlyMovementTest {

    private TeleportOnlyMovement movement;
    private World world;
    private Entity entity;
    private Player owner;

    @BeforeEach
    void setUp() {
        movement = new TeleportOnlyMovement();
        world = mock(World.class);
        entity = mock(Entity.class);
        owner = mock(Player.class);
        when(entity.isValid()).thenReturn(true);
        when(owner.isOnline()).thenReturn(true);
    }

    private ActivePet pet(PetFollowMode mode, double teleportDistance) {
        ActivePet active = new ActivePet(UUID.randomUUID(), UUID.randomUUID(), "orb", 1,
                UUID.randomUUID(), entity, PetRuntimeState.ACTIVE);
        active.setMovementType(PetMovementType.TELEPORT_ONLY);
        active.setMovementDefinition(new PetMovementDefinition(PetMovementType.TELEPORT_ONLY,
                0.0, teleportDistance, 0, 0.0, 0.0, 0.0, null));
        active.setFollowMode(mode);
        return active;
    }

    private void positions(Location petLocation, Location ownerLocation) {
        when(entity.getLocation()).thenReturn(petLocation);
        when(owner.getLocation()).thenReturn(ownerLocation);
    }

    @Test
    void staysPutWhileTheOwnerIsWithinRange() {
        positions(new Location(world, 0.0, 64.0, 0.0), new Location(world, 5.0, 64.0, 0.0));

        movement.tick(pet(PetFollowMode.FOLLOW, 24.0), entity, owner);

        verify(entity, never()).teleport(any(Location.class));
    }

    @Test
    void snapsToTheOwnerOnceOutOfRange() {
        positions(new Location(world, 0.0, 64.0, 0.0), new Location(world, 30.0, 64.0, 0.0));

        movement.tick(pet(PetFollowMode.FOLLOW, 24.0), entity, owner);

        ArgumentCaptor<Location> target = ArgumentCaptor.forClass(Location.class);
        verify(entity).teleport(target.capture());
        assertEquals(30.0, target.getValue().getX(), 1e-9);
        assertEquals(65.0, target.getValue().getY(), 1e-9, "sahibin bir blok üzerine ışınlanmalı");
    }

    @Test
    void differentWorldAlwaysTeleportsEvenWhenCoordinatesAreClose() {
        World otherWorld = mock(World.class);
        positions(new Location(otherWorld, 0.0, 64.0, 0.0), new Location(world, 1.0, 64.0, 0.0));

        movement.tick(pet(PetFollowMode.FOLLOW, 24.0), entity, owner);

        verify(entity).teleport(any(Location.class));
    }

    @Test
    void stayAndWanderModesNeverTeleport() {
        for (PetFollowMode mode : new PetFollowMode[]{PetFollowMode.STAY, PetFollowMode.WANDER}) {
            positions(new Location(world, 0.0, 64.0, 0.0), new Location(world, 500.0, 64.0, 0.0));

            movement.tick(pet(mode, 24.0), entity, owner);
        }

        verify(entity, never()).teleport(any(Location.class));
    }

    @Test
    void unsetTeleportDistanceFallsBackToTheDefault() {
        // Default is 24 blocks: 20 stays, 30 snaps.
        positions(new Location(world, 0.0, 64.0, 0.0), new Location(world, 20.0, 64.0, 0.0));
        movement.tick(pet(PetFollowMode.FOLLOW, 0.0), entity, owner);
        verify(entity, never()).teleport(any(Location.class));

        positions(new Location(world, 0.0, 64.0, 0.0), new Location(world, 30.0, 64.0, 0.0));
        movement.tick(pet(PetFollowMode.FOLLOW, 0.0), entity, owner);
        verify(entity).teleport(any(Location.class));
    }

    @Test
    void invalidEntityOrOfflineOwnerIsIgnored() {
        positions(new Location(world, 0.0, 64.0, 0.0), new Location(world, 500.0, 64.0, 0.0));

        when(entity.isValid()).thenReturn(false);
        movement.tick(pet(PetFollowMode.FOLLOW, 24.0), entity, owner);

        when(entity.isValid()).thenReturn(true);
        when(owner.isOnline()).thenReturn(false);
        movement.tick(pet(PetFollowMode.FOLLOW, 24.0), entity, owner);

        movement.tick(pet(PetFollowMode.FOLLOW, 24.0), null, owner);

        verify(entity, never()).teleport(any(Location.class));
    }
}

package com.petsistemi.runtime;

import com.petsistemi.config.PluginConfiguration;
import com.petsistemi.config.RuntimeConfigurationSnapshot;
import com.petsistemi.domain.PetMovementDefinition;
import com.petsistemi.domain.PetMovementType;
import com.petsistemi.domain.PetRuntimeState;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Follow geometry precedence: per-pet {@code movement} overrides beat {@code config.yml
 * runtime.*}, which beats the constructor defaults. Getting this wrong makes pets either
 * glue themselves to the owner or never catch up, with nothing failing loudly.
 */
class GroundFollowMovementTest {

    private static final int TELEPORT_SQ = 0;
    private static final int STOP_SQ = 1;
    private static final int START_SQ = 2;
    private static final int SPEED = 3;

    private static ActivePet petWith(PetMovementDefinition movement) {
        ActivePet pet = new ActivePet(UUID.randomUUID(), UUID.randomUUID(), "wolf", 1,
                UUID.randomUUID(), null, PetRuntimeState.ACTIVE);
        pet.setMovementDefinition(movement);
        pet.setMovementType(PetMovementType.GROUND_FOLLOW);
        return pet;
    }

    private static PetMovementDefinition movement(double followDistance, double teleportDistance, double followSpeed) {
        return new PetMovementDefinition(PetMovementType.GROUND_FOLLOW,
                followDistance, teleportDistance, 0, 0.0, 0.0, followSpeed, null);
    }

    @Test
    void constructorDefaultsApplyWhenNothingOverridesThem() {
        GroundFollowMovement movement = new GroundFollowMovement(20.0, 2.0, 3.5, 1.2);

        double[] d = movement.distances(petWith(null));

        assertEquals(400.0, d[TELEPORT_SQ], 1e-9);
        assertEquals(4.0, d[STOP_SQ], 1e-9);
        assertEquals(12.25, d[START_SQ], 1e-9);
        assertEquals(1.2, d[SPEED], 1e-9);
    }

    @Test
    void perPetMovementOverridesConstructorDefaults() {
        GroundFollowMovement movement = new GroundFollowMovement(20.0, 2.0, 3.5, 1.2);

        double[] d = movement.distances(petWith(movement(6.0, 30.0, 0.8)));

        assertEquals(900.0, d[TELEPORT_SQ], 1e-9, "teleport-distance ezilmeli");
        assertEquals(36.0, d[START_SQ], 1e-9, "follow-distance start mesafesi olmalı");
        assertEquals(0.8, d[SPEED], 1e-9, "follow-speed ezilmeli");
    }

    /** stop is derived as 0.6x follow-distance so the pet keeps a gap instead of clipping the owner. */
    @Test
    void stopDistanceIsDerivedFromFollowDistance() {
        GroundFollowMovement movement = new GroundFollowMovement();

        double[] d = movement.distances(petWith(movement(5.0, 0.0, 0.0)));

        assertEquals(Math.pow(3.0, 2.0), d[STOP_SQ], 1e-9);
        assertTrue(d[STOP_SQ] < d[START_SQ], "stop < start olmalı, aksi halde histerezis ters döner");
    }

    /** A tiny follow-distance must not drive stop below 1 block, or the pet jitters inside the owner. */
    @Test
    void derivedStopDistanceIsClampedToOneBlock() {
        GroundFollowMovement movement = new GroundFollowMovement();

        double[] d = movement.distances(petWith(movement(0.5, 0.0, 0.0)));

        assertEquals(1.0, d[STOP_SQ], 1e-9);
    }

    @Test
    void zeroFieldsMeanUnsetAndDoNotOverride() {
        GroundFollowMovement movement = new GroundFollowMovement(20.0, 2.0, 3.5, 1.2);

        double[] d = movement.distances(petWith(movement(0.0, 0.0, 0.0)));

        assertEquals(400.0, d[TELEPORT_SQ], 1e-9);
        assertEquals(4.0, d[STOP_SQ], 1e-9);
        assertEquals(12.25, d[START_SQ], 1e-9);
        assertEquals(1.2, d[SPEED], 1e-9);
    }

    @Test
    void configSnapshotBeatsConstructorDefaultsButLosesToPerPetOverrides() {
        AtomicReference<RuntimeConfigurationSnapshot> snapshot = snapshotWith(40.0, 3.0, 7.0, 1.5);
        GroundFollowMovement movement = new GroundFollowMovement(snapshot);

        double[] fromConfig = movement.distances(petWith(null));
        assertEquals(1600.0, fromConfig[TELEPORT_SQ], 1e-9);
        assertEquals(9.0, fromConfig[STOP_SQ], 1e-9);
        assertEquals(49.0, fromConfig[START_SQ], 1e-9);
        assertEquals(1.5, fromConfig[SPEED], 1e-9);

        double[] fromPet = movement.distances(petWith(movement(6.0, 30.0, 0.8)));
        assertEquals(900.0, fromPet[TELEPORT_SQ], 1e-9, "pet tanımı config'i ezmeli");
        assertEquals(36.0, fromPet[START_SQ], 1e-9);
        assertEquals(0.8, fromPet[SPEED], 1e-9);
    }

    @Test
    void nullSnapshotContentFallsBackToDefaults() {
        GroundFollowMovement movement = new GroundFollowMovement(new AtomicReference<>(null));

        double[] d = movement.distances(petWith(null));

        assertEquals(400.0, d[TELEPORT_SQ], 1e-9);
        assertEquals(1.2, d[SPEED], 1e-9);
    }

    private static AtomicReference<RuntimeConfigurationSnapshot> snapshotWith(
            double teleport, double stop, double start, double speed) {
        PluginConfiguration configuration = new PluginConfiguration(
                "tr_TR",
                new PluginConfiguration.LimitsConfiguration(10),
                new PluginConfiguration.NamingConfiguration(3, 16, true, true),
                new PluginConfiguration.ProgressionConfiguration(true, 100),
                new PluginConfiguration.RuntimeConfiguration(5L, start, stop, teleport, speed),
                new PluginConfiguration.DatabaseConfiguration(true, false, 5));
        return new AtomicReference<>(
                new RuntimeConfigurationSnapshot(configuration, null, null, System.currentTimeMillis()));
    }
}

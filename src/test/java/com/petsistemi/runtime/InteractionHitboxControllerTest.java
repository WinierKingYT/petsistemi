package com.petsistemi.runtime;

import com.petsistemi.domain.PetRuntimeState;
import com.petsistemi.domain.RuntimeRepresentationType;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Hitbox spawning is permanently disabled; this controller is now only a cleanup path for
 * hitboxes left over from older versions in existing worlds.
 *
 * <p>The contract these tests pin down is "never creates, always forgets": if the controller
 * ever starts producing entities again it must also mark them non-persistent and tag them
 * with {@code pet_id}, otherwise they become invisible clickable boxes that no code path —
 * not shutdown, not OrphanCleanerTask — can find or remove.</p>
 */
class InteractionHitboxControllerTest {

    private InteractionHitboxController controller;

    @BeforeEach
    void setUp() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getName()).thenReturn("petsistemi");
        controller = new InteractionHitboxController(plugin);
    }

    private static ActivePet spawnedPet() {
        Entity body = mock(Entity.class);
        when(body.isValid()).thenReturn(true);

        ActivePet pet = new ActivePet(UUID.randomUUID(), UUID.randomUUID(), "spirit_flame", 1,
                UUID.randomUUID(), body, PetRuntimeState.ACTIVE);
        pet.setRepresentationType(RuntimeRepresentationType.PARTICLE);
        return pet;
    }

    @Test
    void tickingAPetNeverCreatesAHitbox() {
        ActivePet pet = spawnedPet();

        for (int i = 0; i < 10; i++) {
            controller.updateHitbox(pet, null);
        }

        assertEquals(0, controller.trackedCount(), "hitbox üretimi kapalı olmalı");
        assertEquals(0, controller.mappingCount());
    }

    @Test
    void updateIsSafeWithoutAPet() {
        controller.updateHitbox(null, null);

        assertEquals(0, controller.trackedCount());
    }

    @Test
    void removingAnUntrackedPetIsANoOp() {
        controller.removeHitbox(UUID.randomUUID());
        controller.removeHitbox(null);

        assertEquals(0, controller.trackedCount());
        assertEquals(0, controller.mappingCount());
    }

    @Test
    void unknownHitboxEntitiesResolveToNothing() {
        assertNull(controller.getPetIdFromHitbox(UUID.randomUUID()));
        assertNull(controller.getPetIdFromHitbox(null));
    }

    @Test
    void removeAllOnAnEmptyControllerIsSafe() {
        controller.removeAll();

        assertEquals(0, controller.trackedCount());
        assertEquals(0, controller.mappingCount());
    }

    /** Bookkeeping must never outlive the entities it describes. */
    @Test
    void trackingAndMappingStayInStep() {
        ActivePet pet = spawnedPet();

        controller.updateHitbox(pet, null);
        controller.removeHitbox(pet.getPetId());
        controller.removeAll();

        assertEquals(controller.trackedCount(), controller.mappingCount());
        assertEquals(0, controller.trackedCount());
    }
}

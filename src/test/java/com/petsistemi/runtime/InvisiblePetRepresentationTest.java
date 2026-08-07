package com.petsistemi.runtime;

import com.petsistemi.domain.PetAvailabilityState;
import com.petsistemi.domain.PetDefinition;
import com.petsistemi.domain.PetInstance;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * An INVISIBLE pet still owns a real marker entity — that marker is what the watchdog,
 * the restore path and the movement controllers act on, so its lifecycle must hold.
 */
class InvisiblePetRepresentationTest {

    private InvisiblePetRepresentation representation;
    private World world;
    private Player owner;

    @BeforeEach
    void setUp() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getName()).thenReturn("petsistemi");
        representation = new InvisiblePetRepresentation(plugin);

        world = mock(World.class);
        owner = mock(Player.class);
        when(owner.getWorld()).thenReturn(world);
        when(owner.getLocation()).thenReturn(new Location(world, 1.0, 64.0, 2.0));
    }

    private static PetInstance instance() {
        long now = System.currentTimeMillis();
        return new PetInstance(UUID.randomUUID(), UUID.randomUUID(), "ghost", "Hayalet",
                1, 0, PetAvailabilityState.AVAILABLE, now, now);
    }

    private static PetDefinition definition() {
        return new PetDefinition("ghost", "Hayalet", List.of(), "WOLF",
                false, false, true, false, true, true, 100, false, List.of("{pet_name}"));
    }

    private Entity stubMarker() {
        Entity marker = mock(Entity.class);
        when(marker.getPersistentDataContainer()).thenReturn(mock(PersistentDataContainer.class));
        when(world.spawnEntity(any(Location.class), any(EntityType.class))).thenReturn(marker);
        return marker;
    }

    @Test
    void spawnsAMarkerAtTheOwnerAndHardensIt() {
        Entity marker = stubMarker();

        Entity spawned = representation.spawn(instance(), definition(), owner);

        assertEquals(marker, spawned);
        ArgumentCaptor<EntityType> type = ArgumentCaptor.forClass(EntityType.class);
        verify(world).spawnEntity(any(Location.class), type.capture());
        assertEquals(EntityType.MARKER, type.getValue(), "görünmez pet MARKER kullanmalı");

        verify(marker).setInvulnerable(true);
        verify(marker).setSilent(true);
        // Pets are runtime-owned: a persisted marker would survive a crash as an orphan.
        verify(marker).setPersistent(false);
    }

    @Test
    void spawnTagsTheMarkerSoItCanBeResolvedLater() {
        Entity marker = stubMarker();

        representation.spawn(instance(), definition(), owner);

        verify(marker, org.mockito.Mockito.atLeastOnce()).getPersistentDataContainer();
    }

    @Test
    void removeDeletesAValidMarker() {
        Entity marker = mock(Entity.class);
        when(marker.isValid()).thenReturn(true);

        representation.remove(marker);

        verify(marker).remove();
    }

    @Test
    void removeIsSafeOnAnAlreadyGoneMarker() {
        Entity marker = mock(Entity.class);
        when(marker.isValid()).thenReturn(false);

        representation.remove(marker);
        representation.remove(null);

        verify(marker, never()).remove();
    }

    @Test
    void validityFollowsTheMarker() {
        Entity marker = mock(Entity.class);

        when(marker.isValid()).thenReturn(true);
        assertTrue(representation.isValid(marker));

        when(marker.isValid()).thenReturn(false);
        assertFalse(representation.isValid(marker));

        assertFalse(representation.isValid(null));
    }
}

package com.petsistemi.runtime;

import com.petsistemi.domain.PetAvailabilityState;
import com.petsistemi.domain.PetDefinition;
import com.petsistemi.domain.PetInstance;
import com.petsistemi.domain.PetRepresentationDefinition;
import com.petsistemi.domain.PetVector3;
import com.petsistemi.domain.RuntimeRepresentationType;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A swarm is one pet made of several entities. Miscounting children either leaks
 * entities into the world or leaves the formation half empty.
 */
class MultiEntityPetRepresentationTest {

    private static final int MAX_CHILDREN = 8;

    private MultiEntityPetRepresentation representation;
    private Player owner;

    @BeforeEach
    void setUp() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getName()).thenReturn("petsistemi");
        representation = new MultiEntityPetRepresentation(plugin);

        World world = mock(World.class);
        owner = mock(Player.class);
        when(owner.getWorld()).thenReturn(world);
        when(owner.getLocation()).thenReturn(new Location(world, 0.0, 64.0, 0.0));

        // Every spawn request yields a fresh display with a usable data container.
        when(world.spawnEntity(any(Location.class), any(EntityType.class))).thenAnswer(invocation -> {
            ItemDisplay display = mock(ItemDisplay.class);
            when(display.getPersistentDataContainer()).thenReturn(mock(PersistentDataContainer.class));
            return display;
        });
    }

    private static PetInstance instance() {
        long now = System.currentTimeMillis();
        return new PetInstance(UUID.randomUUID(), UUID.randomUUID(), "familiar_swarm", "Sürü",
                1, 0, PetAvailabilityState.AVAILABLE, now, now);
    }

    private static PetDefinition swarm(int childCount, String childMaterial) {
        return new PetDefinition("familiar_swarm", "Familiar Swarm", List.of(), "WOLF",
                false, false, true, false, true, true, 100, false, List.of("{pet_name}"),
                new PetRepresentationDefinition(RuntimeRepresentationType.MULTI_ENTITY, "WOLF",
                        false, false, true, false, true, "ALLIUM", null, PetVector3.ONE,
                        null, 0, 0.0, 0.0, childCount, childMaterial),
                null);
    }

    private List<Entity> children(int childCount, String childMaterial) {
        return representation.spawnChildren(mock(ItemDisplay.class), instance(),
                swarm(childCount, childMaterial), owner);
    }

    @Test
    void spawnsExactlyTheConfiguredNumberOfChildren() {
        assertEquals(3, children(3, "POPPY").size());
        assertEquals(1, children(1, "POPPY").size());
    }

    @Test
    void zeroChildrenSpawnsNothing() {
        assertTrue(children(0, "POPPY").isEmpty());
    }

    /** The validator caps child-count at 8, but a malformed definition must not spawn a swarm of 500. */
    @Test
    void childCountIsClampedToTheMaximum() {
        assertEquals(MAX_CHILDREN, children(500, "POPPY").size());
    }

    @Test
    void negativeChildCountIsTreatedAsZero() {
        assertTrue(children(-4, "POPPY").isEmpty());
    }

    @Test
    void unknownChildMaterialFallsBackInsteadOfThrowing() {
        assertEquals(2, children(2, "NOT_A_MATERIAL").size());
    }

    @Test
    void missingChildMaterialFallsBackToTheDefault() {
        assertEquals(2, children(2, null).size());
    }

    @Test
    void everyChildIsADistinctEntity() {
        List<Entity> spawned = children(4, "POPPY");

        assertEquals(4, spawned.stream().distinct().count(), "çocuklar aynı entity'yi paylaşmamalı");
    }
}

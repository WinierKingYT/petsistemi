package com.petsistemi.runtime;

import com.petsistemi.domain.PetAvailabilityState;
import com.petsistemi.domain.PetDefinition;
import com.petsistemi.domain.PetInstance;
import com.petsistemi.domain.PetRepresentationDefinition;
import com.petsistemi.domain.PetVector3;
import com.petsistemi.domain.RuntimeRepresentationType;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Transformation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Rest (idle/sleep) shrinks display pets by a fixed factor. All four display-backed
 * representations must agree on that factor, otherwise a sleeping pet changes size
 * depending on which representation it happens to use.
 */
class DisplayRestScaleTest {

    private static final double REST_FACTOR = 0.65;

    private JavaPlugin plugin;

    @BeforeEach
    void setUp() {
        plugin = mock(JavaPlugin.class);
        when(plugin.getName()).thenReturn("petsistemi");
    }

    private static PetInstance instance(int level) {
        long now = System.currentTimeMillis();
        return new PetInstance(UUID.randomUUID(), UUID.randomUUID(), "orb", "Orb",
                level, 0, PetAvailabilityState.AVAILABLE, now, now);
    }

    private static PetDefinition definition(RuntimeRepresentationType type, PetVector3 scale) {
        return new PetDefinition("orb", "Orb", List.of(), "WOLF",
                false, false, true, false, true, true, 100, false, List.of("{pet_name}"),
                new PetRepresentationDefinition(type, "WOLF", false, false, true, false, true,
                        "AMETHYST_SHARD", null, scale),
                null);
    }

    private static PetVector3 capturedScale(Entity display) {
        ArgumentCaptor<Transformation> captor = ArgumentCaptor.forClass(Transformation.class);
        verify((org.bukkit.entity.Display) display).setTransformation(captor.capture());
        Transformation t = captor.getValue();
        return new PetVector3(t.getScale().x(), t.getScale().y(), t.getScale().z());
    }

    @Test
    void restScaleShrinksByTheFixedFactor() {
        PetVector3 base = new PetVector3(1.0, 2.0, 3.0);

        PetVector3 resting = ItemDisplayPetRepresentation.restScale(base, true);

        assertEquals(1.0 * REST_FACTOR, resting.x(), 1e-9);
        assertEquals(2.0 * REST_FACTOR, resting.y(), 1e-9);
        assertEquals(3.0 * REST_FACTOR, resting.z(), 1e-9);
    }

    @Test
    void wakingRestoresTheExactBaseScale() {
        PetVector3 base = new PetVector3(1.2, 1.2, 1.2);

        assertEquals(base, ItemDisplayPetRepresentation.restScale(base, false));
    }

    @Test
    void itemDisplayAppliesTheRestScale() {
        ItemDisplay display = mock(ItemDisplay.class);

        new ItemDisplayPetRepresentation(plugin).applyRestState(display, instance(1),
                definition(RuntimeRepresentationType.ITEM_DISPLAY, new PetVector3(1.0, 1.0, 1.0)), true);

        assertEquals(REST_FACTOR, capturedScale(display).x(), 1e-6);
    }

    @Test
    void blockDisplayAppliesTheSameRestScale() {
        BlockDisplay display = mock(BlockDisplay.class);

        new BlockDisplayPetRepresentation(plugin).applyRestState(display, instance(1),
                definition(RuntimeRepresentationType.BLOCK_DISPLAY, new PetVector3(1.0, 1.0, 1.0)), true);

        assertEquals(REST_FACTOR, capturedScale(display).x(), 1e-6);
    }

    @Test
    void textDisplayAppliesTheSameRestScale() {
        TextDisplay display = mock(TextDisplay.class);

        new TextDisplayPetRepresentation(plugin).applyRestState(display, instance(1),
                definition(RuntimeRepresentationType.TEXT_DISPLAY, new PetVector3(1.0, 1.0, 1.0)), true);

        assertEquals(REST_FACTOR, capturedScale(display).x(), 1e-6);
    }

    @Test
    void multiEntityAppliesTheSameRestScale() {
        ItemDisplay display = mock(ItemDisplay.class);

        new MultiEntityPetRepresentation(plugin).applyRestState(display, instance(1),
                definition(RuntimeRepresentationType.MULTI_ENTITY, new PetVector3(1.0, 1.0, 1.0)), true);

        assertEquals(REST_FACTOR, capturedScale(display).x(), 1e-6);
    }

    /** A representation must never reshape an entity that isn't its own display type. */
    @Test
    void mismatchedEntityTypeIsLeftAlone() {
        BlockDisplay wrongType = mock(BlockDisplay.class);

        new ItemDisplayPetRepresentation(plugin).applyRestState(wrongType, instance(1),
                definition(RuntimeRepresentationType.ITEM_DISPLAY, new PetVector3(1.0, 1.0, 1.0)), true);

        verify(wrongType, never()).setTransformation(any(Transformation.class));
    }

    @Test
    void restScaleCompoundsWithTheBaseScaleRatherThanReplacingIt() {
        ItemDisplay display = mock(ItemDisplay.class);

        new ItemDisplayPetRepresentation(plugin).applyRestState(display, instance(1),
                definition(RuntimeRepresentationType.ITEM_DISPLAY, new PetVector3(2.0, 2.0, 2.0)), true);

        assertEquals(2.0 * REST_FACTOR, capturedScale(display).x(), 1e-6);
    }
}

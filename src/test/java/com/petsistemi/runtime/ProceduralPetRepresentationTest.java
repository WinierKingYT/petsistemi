package com.petsistemi.runtime;

import com.petsistemi.domain.PetAvailabilityState;
import com.petsistemi.domain.PetDefinition;
import com.petsistemi.domain.PetInstance;
import com.petsistemi.domain.PetRepresentationDefinition;
import com.petsistemi.domain.PetVector3;
import com.petsistemi.domain.RuntimeRepresentationType;
import com.petsistemi.domain.visual.PetProceduralDefinition;
import com.petsistemi.domain.visual.PetProceduralShape;
import com.petsistemi.domain.visual.PetVisualTransform;
import com.petsistemi.runtime.visual.PetRenderBackend;
import com.petsistemi.runtime.visual.PetVisualComponent;
import com.petsistemi.runtime.visual.PetVisualHandle;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProceduralPetRepresentationTest {

    @Test
    void generatedNodesRotatePulseAndCleanUpThroughComposite() {
        CompositePetRepresentation composite = mock(CompositePetRepresentation.class);
        World world = mock(World.class);
        Entity root = mock(Entity.class);
        when(root.getUniqueId()).thenReturn(UUID.randomUUID());
        when(root.isValid()).thenReturn(true);
        when(root.isDead()).thenReturn(false);
        when(root.getLocation()).thenReturn(new Location(world, 10, 70, -4));
        PetVisualHandle.Builder builder = PetVisualHandle.builder("root", PetRenderBackend.SERVER)
                .component(component("root", null, root));
        List<ItemDisplay> displays = new ArrayList<>();
        for (int index = 1; index <= 4; index++) {
            ItemDisplay display = mock(ItemDisplay.class);
            when(display.getUniqueId()).thenReturn(UUID.randomUUID());
            when(display.isValid()).thenReturn(true);
            when(display.isDead()).thenReturn(false);
            when(display.getTransformation()).thenReturn(identity());
            displays.add(display);
            builder.component(component(String.format("point-%02d", index), "root", display));
        }
        PetVisualHandle handle = builder.build();
        when(composite.spawnVisual(any(), any(), any())).thenReturn(handle);
        when(composite.isVisualHandleValid(handle)).thenReturn(true);
        ProceduralPetRepresentation controller = new ProceduralPetRepresentation(composite);
        PetDefinition definition = definition(PetProceduralShape.RING);
        PetInstance pet = pet();
        Player owner = mock(Player.class);

        assertSame(handle, controller.spawnVisual(pet, definition, owner));
        ArgumentCaptor<PetDefinition> inner = ArgumentCaptor.forClass(PetDefinition.class);
        verify(composite).spawnVisual(eq(pet), inner.capture(), eq(owner));
        assertEquals(RuntimeRepresentationType.COMPOSITE, inner.getValue().representation().type());
        assertEquals(5, inner.getValue().representation().visualGraph().nodes().size());

        controller.tickVisualHandle(handle, pet, definition, owner);
        verify(composite, never()).tickVisualHandle(any(), any(), any(), any());
        ArgumentCaptor<Location> positions = ArgumentCaptor.forClass(Location.class);
        verify(displays.get(0), atLeast(2)).teleport(positions.capture());
        Location rotated = positions.getAllValues().get(positions.getAllValues().size() - 1);
        assertEquals(10.0, rotated.getX(), 0.02);
        assertEquals(-3.0, rotated.getZ(), 0.02);
        verify(displays.get(0), atLeastOnce()).setTransformation(any(Transformation.class));
        assertTrue(controller.isVisualHandleValid(handle));

        controller.removeVisualHandle(handle);
        verify(composite).removeVisualHandle(handle);
    }

    @Test
    void everyBuiltInShapeProducesStableFiniteNodeCount() {
        for (PetProceduralShape shape : PetProceduralShape.values()) {
            PetProceduralDefinition definition = procedural(shape);
            List<PetVector3> first = ProceduralPetRepresentation.sample(definition, 30.0);
            List<PetVector3> second = ProceduralPetRepresentation.sample(definition, 30.0);
            assertEquals(12, first.size(), shape.name());
            assertEquals(first, second, shape.name());
            assertTrue(first.stream().allMatch(point -> Double.isFinite(point.x())
                    && Double.isFinite(point.y()) && Double.isFinite(point.z())), shape.name());
        }
    }

    private static PetDefinition definition(PetProceduralShape shape) {
        PetProceduralDefinition procedural = new PetProceduralDefinition(shape, 4, 1.0, 1.0,
                90.0, 0.1, 4.0, 1, PetRepresentationDefinition.display(
                RuntimeRepresentationType.ITEM_DISPLAY, "AMETHYST_SHARD", 15101,
                new PetVector3(0.2, 0.2, 0.2)));
        return PetDefinition.builder("arcane_galaxy", "Arcane Galaxy")
                .representation(PetRepresentationDefinition.procedural(procedural, PetVector3.ONE)).build();
    }

    private static PetProceduralDefinition procedural(PetProceduralShape shape) {
        return new PetProceduralDefinition(shape, 12, 1.0, 1.2, 2.0,
                0.1, 4.0, 1, PetRepresentationDefinition.display(
                RuntimeRepresentationType.ITEM_DISPLAY, "AMETHYST_SHARD", null, PetVector3.ONE));
    }

    private static PetVisualComponent component(String id, String parent, Entity entity) {
        return new PetVisualComponent(id, parent, NamespacedKey.minecraft("item_display"),
                PetVisualTransform.IDENTITY, entity);
    }

    private static Transformation identity() {
        return new Transformation(new Vector3f(), new Quaternionf(),
                new Vector3f(1, 1, 1), new Quaternionf());
    }

    private static PetInstance pet() {
        UUID owner = UUID.randomUUID();
        return new PetInstance(UUID.randomUUID(), owner, "arcane_galaxy", "Galaxy", 1, 0,
                PetAvailabilityState.AVAILABLE, 0, 0);
    }
}

package com.petsistemi.runtime;

import com.petsistemi.domain.PetAvailabilityState;
import com.petsistemi.domain.PetDefinition;
import com.petsistemi.domain.PetInstance;
import com.petsistemi.domain.PetRepresentationDefinition;
import com.petsistemi.domain.PetVector3;
import com.petsistemi.domain.RuntimeRepresentationType;
import com.petsistemi.domain.animation.PetAnimationState;
import com.petsistemi.domain.visual.PetDisplayAnimationDefinition;
import com.petsistemi.domain.visual.PetDisplayKeyframeDefinition;
import com.petsistemi.domain.visual.PetDisplayModelDefinition;
import com.petsistemi.domain.visual.PetVisualGraphDefinition;
import com.petsistemi.domain.visual.PetVisualNodeDefinition;
import com.petsistemi.domain.visual.PetVisualTransform;
import com.petsistemi.runtime.visual.PetVisualHandle;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DisplayModelPetRepresentationTest {

    @Test
    void parentAnimationRotatesChildPositionAndAppliesHierarchicalScale() {
        World world = mock(World.class);
        ItemDisplay body = display(world, new Location(world, 0, 64, 0));
        ItemDisplay wing = display(world, new Location(world, 0, 64, 0));
        Queue<ItemDisplay> spawned = new ArrayDeque<>(List.of(body, wing));
        int[] removals = {0};
        PetRepresentationController atomic = new PetRepresentationController() {
            @Override public Entity spawn(PetInstance pet, PetDefinition definition, Player owner) { return spawned.remove(); }
            @Override public void updateVisual(Entity entity, PetInstance pet, PetDefinition definition) { }
            @Override public void remove(Entity entity) { removals[0]++; }
            @Override public boolean isValid(Entity entity) { return entity != null && entity.isValid(); }
        };
        PetRepresentationRegistry registry = new PetRepresentationRegistry();
        registry.register(RuntimeRepresentationType.ITEM_DISPLAY, atomic);
        CompositePetRepresentation composite = new CompositePetRepresentation(registry);
        DisplayModelPetRepresentation modelController = new DisplayModelPetRepresentation(composite);

        PetDefinition definition = definition();
        PetVisualHandle handle = modelController.spawnVisual(pet(), definition, mock(Player.class));
        for (int i = 0; i <= 10; i++) {
            modelController.tickVisualHandle(handle, pet(), definition, mock(Player.class));
        }

        ArgumentCaptor<Location> locations = ArgumentCaptor.forClass(Location.class);
        verify(wing, atLeast(2)).teleport(locations.capture());
        Location animated = locations.getAllValues().get(locations.getAllValues().size() - 1);
        assertEquals(0.0, animated.getX(), 0.02);
        assertEquals(1.0, Math.abs(animated.getZ()), 0.02);

        ArgumentCaptor<Transformation> transformations = ArgumentCaptor.forClass(Transformation.class);
        verify(wing, atLeastOnce()).setTransformation(transformations.capture());
        Transformation wingTransform = transformations.getAllValues().get(transformations.getAllValues().size() - 1);
        assertEquals(0.5f, wingTransform.getScale().x(), 0.001f);
        verify(body).setBillboard(Display.Billboard.FIXED);
        verify(wing).setBillboard(Display.Billboard.FIXED);

        modelController.removeVisualHandle(handle);
        assertEquals(2, removals[0]);
    }

    private static PetDefinition definition() {
        PetRepresentationDefinition item = PetRepresentationDefinition.display(
                RuntimeRepresentationType.ITEM_DISPLAY, "IRON_NUGGET", null, PetVector3.ONE);
        PetVisualGraphDefinition skeleton = new PetVisualGraphDefinition("body", List.of(
                new PetVisualNodeDefinition("wing", "body", item,
                        new PetVisualTransform(new PetVector3(1, 0, 0), PetVector3.ZERO,
                                new PetVector3(0.5, 0.5, 0.5))),
                new PetVisualNodeDefinition("body", null, item, PetVisualTransform.IDENTITY)
        ));
        PetDisplayAnimationDefinition idle = new PetDisplayAnimationDefinition(10, false, Map.of(
                "body", List.of(
                        new PetDisplayKeyframeDefinition(0, PetVisualTransform.IDENTITY),
                        new PetDisplayKeyframeDefinition(10, new PetVisualTransform(
                                PetVector3.ZERO, new PetVector3(0, 90, 0), PetVector3.ONE))))) ;
        return PetDefinition.builder("mechanical_bird", "Mechanical Bird")
                .representation(PetRepresentationDefinition.displayModel(
                        new PetDisplayModelDefinition(skeleton, Map.of(PetAnimationState.IDLE, idle))))
                .build();
    }

    private static PetInstance pet() {
        UUID owner = UUID.randomUUID();
        return new PetInstance(UUID.randomUUID(), owner, "mechanical_bird", "Bird", 1, 0,
                PetAvailabilityState.AVAILABLE, 0, 0);
    }

    private static ItemDisplay display(World world, Location location) {
        ItemDisplay display = mock(ItemDisplay.class);
        when(display.getUniqueId()).thenReturn(UUID.randomUUID());
        when(display.isValid()).thenReturn(true);
        when(display.isDead()).thenReturn(false);
        when(display.getWorld()).thenReturn(world);
        when(display.getLocation()).thenReturn(location);
        when(display.getTransformation()).thenReturn(new Transformation(
                new Vector3f(), new Quaternionf(), new Vector3f(1, 1, 1), new Quaternionf()));
        return display;
    }
}

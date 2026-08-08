package com.petsistemi.runtime;

import com.petsistemi.domain.PetAvailabilityState;
import com.petsistemi.domain.PetDefinition;
import com.petsistemi.domain.PetInstance;
import com.petsistemi.domain.PetRepresentationDefinition;
import com.petsistemi.domain.PetVector3;
import com.petsistemi.domain.visual.PetParticleModelDefinition;
import com.petsistemi.domain.visual.PetParticleModelPartDefinition;
import com.petsistemi.domain.visual.PetParticleShape;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ParticleModelPetRepresentationTest {

    @Test
    void rendersAtMarkerAndHonorsUpdateInterval() {
        InvisiblePetRepresentation markerController = mock(InvisiblePetRepresentation.class);
        Entity marker = mock(Entity.class);
        World world = mock(World.class);
        UUID markerId = UUID.randomUUID();
        Location anchor = new Location(world, 10.0, 70.0, -4.0);
        when(marker.getUniqueId()).thenReturn(markerId);
        when(marker.isValid()).thenReturn(true);
        when(marker.getWorld()).thenReturn(world);
        when(marker.getLocation()).thenReturn(anchor);
        when(markerController.spawn(any(), any(), any())).thenReturn(marker);
        when(markerController.isValid(marker)).thenReturn(true);
        Player owner = mock(Player.class);
        when(owner.isOnline()).thenReturn(true);

        ParticleModelPetRepresentation controller = new ParticleModelPetRepresentation(markerController);
        PetDefinition definition = definition(2, PetParticleShape.RING, 4);
        PetInstance pet = pet();
        assertSame(marker, controller.spawn(pet, definition, owner));

        controller.tickVisual(marker, pet, definition, owner);
        controller.tickVisual(marker, pet, definition, owner);
        controller.tickVisual(marker, pet, definition, owner);

        ArgumentCaptor<Location> positions = ArgumentCaptor.forClass(Location.class);
        verify(world, times(8)).spawnParticle(eq(Particle.END_ROD), positions.capture(), eq(1),
                eq(0.0), eq(0.0), eq(0.0), eq(0.0));
        assertEquals(11.0, positions.getAllValues().get(0).getX(), 0.0001);
        assertEquals(70.25, positions.getAllValues().get(0).getY(), 0.0001);
        assertEquals(-4.0, positions.getAllValues().get(0).getZ(), 0.0001);
        assertTrue(controller.isValid(marker));

        controller.remove(marker);
        verify(markerController).remove(marker);
        assertFalse(controller.isValid(marker));
    }

    @Test
    void everyBuiltInShapeProducesFinitePointBudget() {
        for (PetParticleShape shape : PetParticleShape.values()) {
            PetParticleModelPartDefinition part = new PetParticleModelPartDefinition(
                    shape, "END_ROD", 24, 0.8, 1.4, PetVector3.ZERO, 3.0);
            List<PetVector3> points = ParticleModelPetRepresentation.sample(part, 45.0);
            assertEquals(24, points.size(), shape.name());
            assertTrue(points.stream().allMatch(point -> Double.isFinite(point.x())
                    && Double.isFinite(point.y()) && Double.isFinite(point.z())), shape.name());
        }
    }

    private static PetDefinition definition(int interval, PetParticleShape shape, int points) {
        PetParticleModelPartDefinition part = new PetParticleModelPartDefinition(
                shape, "END_ROD", points, 1.0, 1.0, new PetVector3(0.0, 0.25, 0.0), 0.0);
        return PetDefinition.builder("astral_spirit", "Astral Spirit")
                .representation(PetRepresentationDefinition.particleModel(
                        new PetParticleModelDefinition(interval, List.of(part))))
                .build();
    }

    private static PetInstance pet() {
        UUID owner = UUID.randomUUID();
        return new PetInstance(UUID.randomUUID(), owner, "astral_spirit", "Astral", 1, 0,
                PetAvailabilityState.AVAILABLE, 0, 0);
    }
}

package com.petsistemi.runtime;

import com.petsistemi.domain.PetAvailabilityState;
import com.petsistemi.domain.PetDefinition;
import com.petsistemi.domain.PetInstance;
import com.petsistemi.domain.PetRepresentationDefinition;
import com.petsistemi.domain.PetVector3;
import com.petsistemi.domain.RuntimeRepresentationType;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ParticlePetRepresentationTest {

    private JavaPlugin plugin;
    private ParticlePetRepresentation representation;

    @BeforeEach
    void setUp() {
        plugin = mock(JavaPlugin.class);
        when(plugin.getName()).thenReturn("petsistemi");
        representation = new ParticlePetRepresentation(plugin);
    }

    private static PetDefinition particleDefinition(String particleType, int count, double offset, double speed) {
        return new PetDefinition("spirit_flame", "Spirit Flame", List.of(), "WOLF",
                false, false, true, false, true, true, 100, false, List.of("{pet_name}"),
                new PetRepresentationDefinition(RuntimeRepresentationType.PARTICLE, "WOLF",
                        false, false, true, false, true, null, null, PetVector3.ONE,
                        particleType, count, offset, speed, 0, null),
                null);
    }

    private static PetInstance instance() {
        long now = System.currentTimeMillis();
        return new PetInstance(UUID.randomUUID(), UUID.randomUUID(), "spirit_flame", "Alev",
                1, 0, PetAvailabilityState.AVAILABLE, now, now);
    }

    /**
     * The aura is the pet's body. Rendering it at the owner would make movement.type
     * and movement.height visually inert for every PARTICLE pet.
     */
    @Test
    void auraRendersAtThePetEntityNotAtTheOwner() {
        World petWorld = mock(World.class);
        Location petLocation = new Location(petWorld, 10.0, 70.0, -4.0);

        Entity marker = mock(Entity.class);
        when(marker.isValid()).thenReturn(true);
        when(marker.getWorld()).thenReturn(petWorld);
        when(marker.getLocation()).thenReturn(petLocation);

        World ownerWorld = mock(World.class);
        Player owner = mock(Player.class);
        when(owner.isOnline()).thenReturn(true);
        when(owner.getWorld()).thenReturn(ownerWorld);
        when(owner.getLocation()).thenReturn(new Location(ownerWorld, 0.0, 64.0, 0.0));

        representation.tickVisual(marker, instance(), particleDefinition("SOUL_FIRE_FLAME", 6, 0.4, 0.02), owner);

        ArgumentCaptor<Location> where = ArgumentCaptor.forClass(Location.class);
        verify(petWorld).spawnParticle(any(Particle.class), where.capture(),
                anyInt(), anyDouble(), anyDouble(), anyDouble(), anyDouble());
        assertEquals(petLocation, where.getValue(), "parçacıklar petin kendi konumunda çizilmeli");

        verify(ownerWorld, never()).spawnParticle(any(Particle.class), any(Location.class),
                anyInt(), anyDouble(), anyDouble(), anyDouble(), anyDouble());
    }

    @Test
    void configuredCountOffsetAndSpeedAreUsed() {
        World petWorld = mock(World.class);
        Entity marker = mock(Entity.class);
        when(marker.isValid()).thenReturn(true);
        when(marker.getWorld()).thenReturn(petWorld);
        when(marker.getLocation()).thenReturn(new Location(petWorld, 1.0, 2.0, 3.0));

        Player owner = mock(Player.class);
        when(owner.isOnline()).thenReturn(true);

        representation.tickVisual(marker, instance(), particleDefinition("HEART", 9, 0.7, 0.05), owner);

        verify(petWorld).spawnParticle(Particle.HEART, new Location(petWorld, 1.0, 2.0, 3.0),
                9, 0.7, 0.7, 0.7, 0.05);
    }

    @Test
    void unknownParticleNameEmitsNothingInsteadOfThrowing() {
        World petWorld = mock(World.class);
        Entity marker = mock(Entity.class);
        when(marker.isValid()).thenReturn(true);
        when(marker.getWorld()).thenReturn(petWorld);
        when(marker.getLocation()).thenReturn(new Location(petWorld, 0.0, 0.0, 0.0));

        Player owner = mock(Player.class);
        when(owner.isOnline()).thenReturn(true);

        representation.tickVisual(marker, instance(), particleDefinition("NOT_A_PARTICLE", 4, 0.3, 0.02), owner);

        verify(petWorld, never()).spawnParticle(any(Particle.class), any(Location.class),
                anyInt(), anyDouble(), anyDouble(), anyDouble(), anyDouble());
    }

    @Test
    void resolveParticleFallsBackToDefaultWhenUnset() {
        assertSame(Particle.SOUL_FIRE_FLAME, ParticlePetRepresentation.resolveParticle(null));
        assertSame(Particle.SOUL_FIRE_FLAME, ParticlePetRepresentation.resolveParticle("  "));
        assertSame(Particle.HEART, ParticlePetRepresentation.resolveParticle(" heart "));
        assertNull(ParticlePetRepresentation.resolveParticle("NOT_A_PARTICLE"));
    }

    @Test
    void invalidEntityOrOfflineOwnerIsIgnored() {
        World petWorld = mock(World.class);
        Entity dead = mock(Entity.class);
        when(dead.isValid()).thenReturn(false);

        Player owner = mock(Player.class);
        when(owner.isOnline()).thenReturn(true);

        representation.tickVisual(dead, instance(), particleDefinition("HEART", 4, 0.3, 0.02), owner);
        representation.tickVisual(null, instance(), particleDefinition("HEART", 4, 0.3, 0.02), owner);

        verify(petWorld, never()).spawnParticle(any(Particle.class), any(Location.class),
                anyInt(), anyDouble(), anyDouble(), anyDouble(), anyDouble());
    }
}

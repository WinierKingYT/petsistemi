package com.petsistemi.listener;

import com.petsistemi.api.PetSnapshot;
import com.petsistemi.api.event.PetLevelUpEvent;
import com.petsistemi.domain.PetAvailabilityState;
import com.petsistemi.domain.PetRuntimeState;
import com.petsistemi.runtime.ActivePet;
import com.petsistemi.runtime.ActivePetRegistry;
import com.petsistemi.runtime.PetReactionEngine;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PetReactionListenerTest {

    private ActivePetRegistry registry;
    private RecordingEngine engine;
    private PetReactionListener listener;

    private final UUID ownerId = UUID.randomUUID();
    private final UUID petId = UUID.randomUUID();
    private World world;
    private Player player;
    private Entity pet;

    @BeforeEach
    void setUp() {
        registry = new ActivePetRegistry();
        engine = new RecordingEngine();
        listener = new PetReactionListener(registry, engine);

        world = mock(World.class);
        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(ownerId);
        when(player.getWorld()).thenReturn(world);
        when(player.getLocation()).thenReturn(new Location(world, 10, 64, 10));

        pet = mock(Entity.class);
        when(pet.isValid()).thenReturn(true);
        when(pet.isDead()).thenReturn(false);
        when(pet.getWorld()).thenReturn(world);
        when(pet.getLocation()).thenReturn(new Location(world, 11, 64, 10));

        ActivePet active = new ActivePet(petId, ownerId, UUID.randomUUID(), pet, PetRuntimeState.ACTIVE);
        registry.register(active);
    }

    private EntityDamageEvent damageEvent() {
        EntityDamageEvent event = mock(EntityDamageEvent.class);
        when(event.getEntity()).thenReturn(player);
        when(event.isCancelled()).thenReturn(false);
        return event;
    }

    @Test
    void ownerDamageTriggersReactionOnNearbyPet() {
        listener.onOwnerDamage(damageEvent());

        assertEquals(List.of(pet), engine.damageCalls);
    }

    @Test
    void ownerDamageIgnoredWhenPetTooFar() {
        when(pet.getLocation()).thenReturn(new Location(world, 100, 64, 100));

        listener.onOwnerDamage(damageEvent());

        assertTrue(engine.damageCalls.isEmpty());
    }

    @Test
    void ownerDamageIgnoredWithoutActivePet() {
        registry.unregister(ownerId);

        listener.onOwnerDamage(damageEvent());

        assertTrue(engine.damageCalls.isEmpty());
    }

    @Test
    void ownerDamageIgnoredWhenPetDead() {
        when(pet.isDead()).thenReturn(true);

        listener.onOwnerDamage(damageEvent());

        assertTrue(engine.damageCalls.isEmpty());
    }

    @Test
    void levelUpTriggersReactionForActivePet() {
        PetSnapshot snapshot = new PetSnapshot(petId, ownerId, "wolf", "Kurt", 5, 100,
                PetAvailabilityState.AVAILABLE, true, true);
        listener.onLevelUp(new PetLevelUpEvent(snapshot, 4, 5));

        assertEquals(List.of(pet), engine.levelUpCalls);
    }

    @Test
    void levelUpIgnoredWithoutActivePet() {
        registry.unregister(ownerId);

        PetSnapshot snapshot = new PetSnapshot(petId, ownerId, "wolf", "Kurt", 5, 100,
                PetAvailabilityState.AVAILABLE, true, true);
        listener.onLevelUp(new PetLevelUpEvent(snapshot, 4, 5));

        assertTrue(engine.levelUpCalls.isEmpty());
    }

    private static class RecordingEngine extends PetReactionEngine {
        final List<Entity> damageCalls = new ArrayList<>();
        final List<Entity> levelUpCalls = new ArrayList<>();

        RecordingEngine() {
            super(new AtomicReference<>());
        }

        @Override
        public void playOwnerDamage(Entity petEntity, com.petsistemi.domain.PetDefinition definition) {
            damageCalls.add(petEntity);
        }

        @Override
        public void playLevelUp(Entity petEntity, com.petsistemi.domain.PetDefinition definition) {
            levelUpCalls.add(petEntity);
        }
    }
}

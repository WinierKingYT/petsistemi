package com.petsistemi.integration;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.WorldMock;
import com.petsistemi.domain.PetRuntimeState;
import com.petsistemi.listener.PetProtectionListener;
import com.petsistemi.runtime.ActivePet;
import com.petsistemi.runtime.ActivePetRegistry;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A pet is the player's property, not a target. If these guards stop firing the pet can be
 * killed by mobs, burned by lava or lost to a stray arrow — and the owner has no way to get
 * it back except an admin command.
 */
class PetProtectionEventTest {

    private ServerMock server;
    private WorldMock world;
    private ActivePetRegistry registry;
    private PetProtectionListener listener;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        registry = new ActivePetRegistry();
        listener = new PetProtectionListener(registry);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    /** Spawns an entity and registers it as somebody's active pet. */
    private Entity registeredPet() {
        Entity entity = world.spawnEntity(new Location(world, 0, 64, 0), EntityType.WOLF);
        ActivePet active = new ActivePet(UUID.randomUUID(), UUID.randomUUID(), "wolf", 1,
                entity.getUniqueId(), entity, PetRuntimeState.ACTIVE);
        registry.register(active);
        return entity;
    }

    /** An identical entity that nobody owns; the listener must ignore it. */
    private Entity unownedEntity() {
        return world.spawnEntity(new Location(world, 5, 64, 5), EntityType.WOLF);
    }

    private static EntityDamageEvent damage(Entity entity, EntityDamageEvent.DamageCause cause) {
        return new EntityDamageEvent(entity, cause, 5.0);
    }

    @Test
    void petsCannotBeDamaged() {
        EntityDamageEvent event = damage(registeredPet(), EntityDamageEvent.DamageCause.ENTITY_ATTACK);

        listener.onDamage(event);

        assertTrue(event.isCancelled(), "pet hasar almamalı");
    }

    @Test
    void environmentalDamageIsAlsoBlocked() {
        for (EntityDamageEvent.DamageCause cause : new EntityDamageEvent.DamageCause[]{
                EntityDamageEvent.DamageCause.LAVA,
                EntityDamageEvent.DamageCause.FALL,
                EntityDamageEvent.DamageCause.DROWNING,
                EntityDamageEvent.DamageCause.VOID}) {
            EntityDamageEvent event = damage(registeredPet(), cause);

            listener.onDamage(event);

            assertTrue(event.isCancelled(), () -> cause + " engellenmeli");
        }
    }

    @Test
    void petsDoNotCatchFire() {
        EntityCombustEvent event = new EntityCombustEvent(registeredPet(), 10);

        listener.onCombust(event);

        assertTrue(event.isCancelled(), "pet yanmamalı");
    }

    /** The control: without it, a listener that cancelled everything would pass the tests above. */
    @Test
    void unownedEntitiesTakeDamageNormally() {
        EntityDamageEvent event = damage(unownedEntity(), EntityDamageEvent.DamageCause.ENTITY_ATTACK);

        listener.onDamage(event);

        assertFalse(event.isCancelled(), "pet olmayan varlıklara karışılmamalı");
    }

    @Test
    void unownedEntitiesStillCombust() {
        EntityCombustEvent event = new EntityCombustEvent(unownedEntity(), 10);

        listener.onCombust(event);

        assertFalse(event.isCancelled());
    }

    /** Once dismissed the entity is no longer protected — otherwise cleanup would be blocked. */
    @Test
    void aDismissedPetLosesItsProtection() {
        Entity entity = registeredPet();
        registry.getByEntity(entity.getUniqueId())
                .ifPresent(pet -> registry.unregister(pet.getOwnerId()));

        EntityDamageEvent event = damage(entity, EntityDamageEvent.DamageCause.ENTITY_ATTACK);
        listener.onDamage(event);

        assertFalse(event.isCancelled());
    }
}

package com.petsistemi.runtime;

import com.petsistemi.definition.PetDefinitionRegistry;
import com.petsistemi.domain.PetDefinition;
import com.petsistemi.domain.PetHitboxDefinition;
import com.petsistemi.domain.PetRepresentationDefinition;
import com.petsistemi.domain.PetRuntimeState;
import com.petsistemi.domain.PetVector3;
import com.petsistemi.domain.RuntimeRepresentationType;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Hitbox entities are runtime-owned. If one is ever written into a chunk — or its
 * bookkeeping outlives the entity — it becomes an invisible clickable box that the
 * plugin can no longer find or delete.
 */
class InteractionHitboxControllerTest {

    private InteractionHitboxController controller;
    private World world;
    private PetDefinitionRegistry registry;
    private final List<Interaction> spawned = new ArrayList<>();

    @BeforeEach
    void setUp() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getName()).thenReturn("petsistemi");
        controller = new InteractionHitboxController(plugin);

        world = mock(World.class);
        // world.spawn(loc, type, consumer) runs the consumer against a fresh mock, like Paper does.
        when(world.spawn(any(Location.class), any(), any(Consumer.class))).thenAnswer(invocation -> {
            Interaction interaction = mock(Interaction.class);
            when(interaction.getUniqueId()).thenReturn(UUID.randomUUID());
            when(interaction.isValid()).thenReturn(true);
            when(interaction.getPersistentDataContainer()).thenReturn(mock(PersistentDataContainer.class));
            @SuppressWarnings("unchecked")
            Consumer<Interaction> consumer = invocation.getArgument(2);
            consumer.accept(interaction);
            spawned.add(interaction);
            return interaction;
        });

        PetDefinition definition = new PetDefinition("spirit_flame", "Spirit Flame", List.of(), "WOLF",
                false, false, true, false, true, true, 100, false, List.of("{pet_name}"),
                new PetRepresentationDefinition(RuntimeRepresentationType.PARTICLE, "WOLF",
                        false, false, true, false, true, null, null, PetVector3.ONE,
                        "SOUL_FIRE_FLAME", 4, 0.3, 0.02, 0, null),
                null,                                    // movement
                null, null, null, null,                  // states, transforms, reactions, emotes
                null, null,                              // guiMaterial, permission
                null, null, null,                        // buffs, personality, evolutions
                new PetHitboxDefinition(true, 0.8f, 0.8f),
                null, null, null, null, null);           // levelRewards, allowedModes, spawnStyle, mount, presence

        registry = new PetDefinitionRegistry() {
            @Override public Optional<PetDefinition> find(String id) { return Optional.of(definition); }
            @Override public Collection<PetDefinition> getAll() { return List.of(definition); }
            @Override public void reload() {}
        };
    }

    private ActivePet spawnedPet() {
        Entity body = mock(Entity.class);
        when(body.isValid()).thenReturn(true);
        when(body.isDead()).thenReturn(false);
        when(body.getLocation()).thenReturn(new Location(world, 0.0, 64.0, 0.0));

        ActivePet pet = new ActivePet(UUID.randomUUID(), UUID.randomUUID(), "spirit_flame", 1,
                UUID.randomUUID(), body, PetRuntimeState.ACTIVE);
        pet.setRepresentationType(RuntimeRepresentationType.PARTICLE);
        return pet;
    }

    /** A persistent hitbox is saved into the chunk and survives restarts as an orphan. */
    @Test
    void spawnedHitboxIsNeverPersisted() {
        controller.updateHitbox(spawnedPet(), registry);

        assertEquals(1, spawned.size());
        verify(spawned.get(0)).setPersistent(false);
    }

    /** Without the pet_id tag, OrphanCleanerTask cannot sweep a stray hitbox. */
    @Test
    void spawnedHitboxIsTaggedSoOrphanCleanerCanFindIt() {
        controller.updateHitbox(spawnedPet(), registry);

        verify(spawned.get(0)).getPersistentDataContainer();
    }

    @Test
    void removeDeletesTheEntityAndClearsBothMaps() {
        ActivePet pet = spawnedPet();
        controller.updateHitbox(pet, registry);
        assertEquals(1, controller.trackedCount());
        assertEquals(1, controller.mappingCount());

        controller.removeHitbox(pet.getPetId());

        verify(spawned.get(0)).remove();
        assertEquals(0, controller.trackedCount());
        assertEquals(0, controller.mappingCount(), "eşleme de temizlenmeli");
    }

    /** The entity can die on its own (chunk unload, /kill); its mapping must still go. */
    @Test
    void removingAnAlreadyDeadHitboxStillClearsItsMapping() {
        ActivePet pet = spawnedPet();
        controller.updateHitbox(pet, registry);
        when(spawned.get(0).isValid()).thenReturn(false);

        controller.removeHitbox(pet.getPetId());

        assertEquals(0, controller.mappingCount(), "ölü entity'nin eşlemesi sızmamalı");
    }

    /** Re-spawning after the old hitbox died must not orphan the old entity's mapping. */
    @Test
    void respawningAfterDeathDoesNotAccumulateMappings() {
        ActivePet pet = spawnedPet();
        controller.updateHitbox(pet, registry);

        for (int i = 0; i < 5; i++) {
            when(spawned.get(spawned.size() - 1).isValid()).thenReturn(false);
            controller.updateHitbox(pet, registry);
        }

        assertEquals(6, spawned.size(), "her ölümde yeniden doğmalı");
        assertEquals(1, controller.trackedCount());
        assertEquals(1, controller.mappingCount(), "eşlemeler birikmemeli");
    }

    @Test
    void hitboxResolvesBackToItsPet() {
        ActivePet pet = spawnedPet();
        controller.updateHitbox(pet, registry);

        UUID hitboxId = spawned.get(0).getUniqueId();
        assertEquals(pet.getPetId(), controller.getPetIdFromHitbox(hitboxId));

        controller.removeHitbox(pet.getPetId());
        assertNull(controller.getPetIdFromHitbox(hitboxId), "silinen hitbox çözümlenmemeli");
    }

    @Test
    void removeAllClearsEverything() {
        ActivePet first = spawnedPet();
        ActivePet second = spawnedPet();
        controller.updateHitbox(first, registry);
        controller.updateHitbox(second, registry);
        assertEquals(2, controller.trackedCount());

        controller.removeAll();

        assertEquals(0, controller.trackedCount());
        assertEquals(0, controller.mappingCount());
        spawned.forEach(interaction -> verify(interaction).remove());
    }

    @Test
    void anInvalidPetGetsNoHitbox() {
        Entity body = mock(Entity.class);
        when(body.isValid()).thenReturn(false);
        ActivePet dead = new ActivePet(UUID.randomUUID(), UUID.randomUUID(), "spirit_flame", 1,
                UUID.randomUUID(), body, PetRuntimeState.ACTIVE);

        controller.updateHitbox(dead, registry);

        assertEquals(0, spawned.size());
        assertEquals(0, controller.trackedCount());
    }

    @Test
    void repeatedTicksReuseTheSameHitboxInsteadOfSpawningMore() {
        ActivePet pet = spawnedPet();

        for (int i = 0; i < 10; i++) {
            controller.updateHitbox(pet, registry);
        }

        assertEquals(1, spawned.size(), "canlı hitbox yeniden kullanılmalı");
        assertNotNull(controller.getPetIdFromHitbox(spawned.get(0).getUniqueId()));
    }
}

package com.petsistemi.task;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * This sweeper deletes entities from the world. Every false positive is a pet that
 * vanishes from a player's game, so the bar for "this is an orphan" is deliberately high.
 */
class OrphanCleanerTaskTest {

    private final Set<UUID> knownPets = new HashSet<>();
    private OrphanCleanerTask task;
    private NamespacedKey petIdKey;

    @BeforeEach
    void setUp() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getName()).thenReturn("petsistemi");
        petIdKey = new NamespacedKey(plugin, "pet_id");
        task = new OrphanCleanerTask(plugin, knownPets::contains);
    }

    /** An entity carrying the given pet_id string, or none at all when {@code tag} is null. */
    private Entity entityTagged(String tag) {
        Entity entity = mock(Entity.class);
        when(entity.isValid()).thenReturn(true);

        PersistentDataContainer pdc = mock(PersistentDataContainer.class);
        when(pdc.has(eq(petIdKey), any())).thenReturn(tag != null);
        when(pdc.get(eq(petIdKey), any())).thenReturn(tag);
        when(entity.getPersistentDataContainer()).thenReturn(pdc);
        return entity;
    }

    @Test
    void untaggedEntitiesAreNeverTouched() {
        assertFalse(task.shouldRemove(entityTagged(null)), "yabancı entity'lere dokunulmamalı");
    }

    @Test
    void aTaggedEntityOfAKnownPetSurvives() {
        UUID petId = UUID.randomUUID();
        knownPets.add(petId);

        assertFalse(task.shouldRemove(entityTagged(petId.toString())));
    }

    @Test
    void aTaggedEntityOfAnUnknownPetIsAnOrphan() {
        assertTrue(task.shouldRemove(entityTagged(UUID.randomUUID().toString())));
    }

    /**
     * The regression this guards: a pet's entity is spawned before its DB commit registers
     * it. If "known" only meant "in the active registry", this sweep deleted pets that were
     * still being summoned.
     */
    @Test
    void aPetThatIsStillBeingSummonedIsNotAnOrphan() {
        UUID midSummon = UUID.randomUUID();
        Entity entity = entityTagged(midSummon.toString());
        assertTrue(task.shouldRemove(entity), "başlangıçta bilinmiyor");

        // Coordinator now reports it as pending (spawned, not yet committed).
        knownPets.add(midSummon);

        assertFalse(task.shouldRemove(entity), "çağrılmakta olan pet silinmemeli");
    }

    @Test
    void malformedPetIdIsLeftAloneRatherThanGuessed() {
        assertFalse(task.shouldRemove(entityTagged("not-a-uuid")));
        assertFalse(task.shouldRemove(entityTagged("")));
    }

    @Test
    void alreadyInvalidEntitiesAreSkipped() {
        Entity entity = entityTagged(UUID.randomUUID().toString());
        when(entity.isValid()).thenReturn(false);

        assertFalse(task.shouldRemove(entity));
    }

    @Test
    void nullEntityIsSkipped() {
        assertFalse(task.shouldRemove(null));
    }

    /** Children and hitboxes carry their parent pet's id, so they must survive with it. */
    @Test
    void childEntitiesSharingTheParentPetIdSurvive() {
        UUID petId = UUID.randomUUID();
        knownPets.add(petId);

        assertFalse(task.shouldRemove(entityTagged(petId.toString())));
        assertFalse(task.shouldRemove(entityTagged(petId.toString())));
    }
}

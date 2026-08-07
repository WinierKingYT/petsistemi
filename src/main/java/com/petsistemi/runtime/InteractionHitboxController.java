package com.petsistemi.runtime;

import com.petsistemi.definition.PetDefinitionRegistry;
import com.petsistemi.domain.PetDefinition;
import com.petsistemi.domain.PetHitboxDefinition;
import com.petsistemi.domain.RuntimeRepresentationType;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Interaction;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages Paper {@link Interaction} entities attached to non-mob pets (ITEM_DISPLAY, PARTICLE, etc.)
 * so that players can right-click them to open inspect GUI or trigger emotes/reactions.
 */
public class InteractionHitboxController {

    private final JavaPlugin plugin;
    private final NamespacedKey petIdKey;
    private final Map<UUID, Interaction> activeHitboxes = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> hitboxToPetId = new ConcurrentHashMap<>();

    public InteractionHitboxController(JavaPlugin plugin) {
        this.plugin = plugin;
        // Same key OrphanCleanerTask scans for, so a stray hitbox is sweepable.
        this.petIdKey = new NamespacedKey(plugin, "pet_id");
    }

    public void updateHitbox(ActivePet pet, PetDefinitionRegistry definitionRegistry) {
        if (pet == null || !pet.isValid() || pet.getSpawnedEntity() == null) {
            removeHitbox(pet != null ? pet.getPetId() : null);
            return;
        }

        PetDefinition def = definitionRegistry != null ? definitionRegistry.find(pet.getDefinitionId()).orElse(null) : null;
        if (def == null) return;

        RuntimeRepresentationType repType = def.representationOrEntity().type();
        PetHitboxDefinition hitboxDef = def.hitbox() != null ? def.hitbox() : PetHitboxDefinition.DEFAULT;

        // Regular living entities already have native collision/interaction.
        if (repType == RuntimeRepresentationType.ENTITY || !hitboxDef.enabled()) {
            removeHitbox(pet.getPetId());
            return;
        }

        Location currentLoc = pet.getSpawnedEntity().getLocation();
        Interaction interaction = activeHitboxes.get(pet.getPetId());

        if (interaction == null || !interaction.isValid()) {
            // Drop the stale mapping first: the dead entity's id would otherwise sit in
            // hitboxToPetId forever, since removeHitbox() only reaches valid entities.
            forgetHitbox(pet.getPetId());
            try {
                UUID petId = pet.getPetId();
                interaction = currentLoc.getWorld().spawn(currentLoc, Interaction.class, entity -> {
                    entity.setInteractionWidth(hitboxDef.width());
                    entity.setInteractionHeight(hitboxDef.height());
                    entity.setResponsive(true);
                    // Runtime-owned, exactly like every other pet entity: never written into
                    // a chunk, and tagged so OrphanCleanerTask can sweep any stray that
                    // outlives the plugin (e.g. after a crash).
                    entity.setPersistent(false);
                    entity.getPersistentDataContainer().set(petIdKey, PersistentDataType.STRING, petId.toString());
                });
                activeHitboxes.put(petId, interaction);
                hitboxToPetId.put(interaction.getUniqueId(), petId);
            } catch (Throwable t) {
                // If Interaction entity is unavailable in current paper API environment
                return;
            }
        } else {
            interaction.teleport(currentLoc);
        }
    }

    public void removeHitbox(UUID petId) {
        if (petId == null) return;
        Interaction interaction = activeHitboxes.remove(petId);
        if (interaction == null) {
            return;
        }
        // Unmap unconditionally — an already-dead entity still owns a hitboxToPetId entry.
        hitboxToPetId.remove(interaction.getUniqueId());
        if (interaction.isValid()) {
            interaction.remove();
        }
    }

    /** Forgets the bookkeeping for a pet's hitbox without touching the entity itself. */
    private void forgetHitbox(UUID petId) {
        Interaction stale = activeHitboxes.remove(petId);
        if (stale != null) {
            hitboxToPetId.remove(stale.getUniqueId());
        }
    }

    public UUID getPetIdFromHitbox(UUID interactionEntityId) {
        return hitboxToPetId.get(interactionEntityId);
    }

    /** Removes every tracked hitbox; called on plugin shutdown so none survive into the world. */
    public void removeAll() {
        for (Interaction interaction : activeHitboxes.values()) {
            if (interaction != null && interaction.isValid()) {
                try {
                    interaction.remove();
                } catch (Exception ignored) {
                    // A single stubborn entity must not abort the sweep.
                }
            }
        }
        activeHitboxes.clear();
        hitboxToPetId.clear();
    }

    /** Number of hitboxes currently tracked; used by tests to assert nothing leaks. */
    int trackedCount() {
        return activeHitboxes.size();
    }

    /** Number of entity→pet mappings currently held; must never outgrow {@link #trackedCount()}. */
    int mappingCount() {
        return hitboxToPetId.size();
    }
}

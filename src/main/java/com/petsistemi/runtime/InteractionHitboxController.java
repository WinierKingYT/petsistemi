package com.petsistemi.runtime;

import com.petsistemi.definition.PetDefinitionRegistry;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Interaction;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks any previously-spawned interaction hitbox entities and removes them on demand.
 * Spawning of new hitbox entities has been permanently disabled: the DEFAULT and DISABLED
 * constants on {@link com.petsistemi.domain.PetHitboxDefinition} both set {@code enabled=false},
 * so this controller now serves only as a cleanup path (e.g. OrphanCleanerTask sweeps).
 */
public class InteractionHitboxController {

    private final NamespacedKey petIdKey;
    private final Map<UUID, Interaction> activeHitboxes = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> hitboxToPetId = new ConcurrentHashMap<>();

    public InteractionHitboxController(JavaPlugin plugin) {
        this.petIdKey = new NamespacedKey(plugin, "pet_id");
    }

    /**
     * No-op: hitbox spawning is permanently disabled.
     * Any existing tracked hitbox for this pet is removed to ensure clean state.
     */
    public void updateHitbox(ActivePet pet, PetDefinitionRegistry definitionRegistry) {
        if (pet != null) {
            removeHitbox(pet.getPetId());
        }
    }

    public void removeHitbox(UUID petId) {
        if (petId == null) return;
        Interaction interaction = activeHitboxes.remove(petId);
        if (interaction == null) return;
        hitboxToPetId.remove(interaction.getUniqueId());
        if (interaction.isValid()) {
            interaction.remove();
        }
    }

    public UUID getPetIdFromHitbox(UUID interactionEntityId) {
        // ConcurrentHashMap#get throws on a null key; callers resolving an arbitrary clicked
        // entity should just get "not a hitbox" back.
        if (interactionEntityId == null) return null;
        return hitboxToPetId.get(interactionEntityId);
    }

    public void removeAll() {
        for (Interaction interaction : activeHitboxes.values()) {
            if (interaction != null && interaction.isValid()) {
                try {
                    interaction.remove();
                } catch (Exception ignored) {}
            }
        }
        activeHitboxes.clear();
        hitboxToPetId.clear();
    }

    int trackedCount() {
        return activeHitboxes.size();
    }

    int mappingCount() {
        return hitboxToPetId.size();
    }
}

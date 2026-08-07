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
 * when explicitly enabled. Defaults to disabled to prevent invisible hitbox clutter in crowded areas.
 */
public class InteractionHitboxController {

    private final JavaPlugin plugin;
    private final NamespacedKey petIdKey;
    private final Map<UUID, Interaction> activeHitboxes = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> hitboxToPetId = new ConcurrentHashMap<>();

    public InteractionHitboxController(JavaPlugin plugin) {
        this.plugin = plugin;
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
        PetHitboxDefinition hitboxDef = def.hitbox() != null ? def.hitbox() : PetHitboxDefinition.DISABLED;

        // Non-entity pets only spawn a hitbox if explicitly enabled in YAML
        if (repType == RuntimeRepresentationType.ENTITY || !hitboxDef.enabled()) {
            removeHitbox(pet.getPetId());
            return;
        }

        Location currentLoc = pet.getSpawnedEntity().getLocation();
        Interaction interaction = activeHitboxes.get(pet.getPetId());

        if (interaction == null || !interaction.isValid()) {
            forgetHitbox(pet.getPetId());
            try {
                UUID petId = pet.getPetId();
                interaction = currentLoc.getWorld().spawn(currentLoc, Interaction.class, entity -> {
                    entity.setInteractionWidth(hitboxDef.width());
                    entity.setInteractionHeight(hitboxDef.height());
                    entity.setResponsive(true);
                    entity.setPersistent(false);
                    entity.getPersistentDataContainer().set(petIdKey, PersistentDataType.STRING, petId.toString());
                });
                activeHitboxes.put(petId, interaction);
                hitboxToPetId.put(interaction.getUniqueId(), petId);
            } catch (Throwable t) {
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
        hitboxToPetId.remove(interaction.getUniqueId());
        if (interaction.isValid()) {
            interaction.remove();
        }
    }

    private void forgetHitbox(UUID petId) {
        Interaction stale = activeHitboxes.remove(petId);
        if (stale != null) {
            hitboxToPetId.remove(stale.getUniqueId());
        }
    }

    public UUID getPetIdFromHitbox(UUID interactionEntityId) {
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

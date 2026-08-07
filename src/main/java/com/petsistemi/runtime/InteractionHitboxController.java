package com.petsistemi.runtime;

import com.petsistemi.definition.PetDefinitionRegistry;
import com.petsistemi.domain.PetDefinition;
import com.petsistemi.domain.PetHitboxDefinition;
import com.petsistemi.domain.RuntimeRepresentationType;
import org.bukkit.Location;
import org.bukkit.entity.Interaction;
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
    private final Map<UUID, Interaction> activeHitboxes = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> hitboxToPetId = new ConcurrentHashMap<>();

    public InteractionHitboxController(JavaPlugin plugin) {
        this.plugin = plugin;
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
            try {
                interaction = currentLoc.getWorld().spawn(currentLoc, Interaction.class, entity -> {
                    entity.setInteractionWidth(hitboxDef.width());
                    entity.setInteractionHeight(hitboxDef.height());
                    entity.setResponsive(true);
                });
                activeHitboxes.put(pet.getPetId(), interaction);
                hitboxToPetId.put(interaction.getUniqueId(), pet.getPetId());
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
        if (interaction != null && interaction.isValid()) {
            hitboxToPetId.remove(interaction.getUniqueId());
            interaction.remove();
        }
    }

    public UUID getPetIdFromHitbox(UUID interactionEntityId) {
        return hitboxToPetId.get(interactionEntityId);
    }

    public void removeAll() {
        for (Interaction interaction : activeHitboxes.values()) {
            if (interaction != null && interaction.isValid()) {
                interaction.remove();
            }
        }
        activeHitboxes.clear();
        hitboxToPetId.clear();
    }
}

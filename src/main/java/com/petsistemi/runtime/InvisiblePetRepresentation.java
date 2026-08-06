package com.petsistemi.runtime;

import com.petsistemi.domain.PetDefinition;
import com.petsistemi.domain.PetInstance;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

/**
 * Invisible representation: a fully invisible (MARKER) pet. Useful for hidden
 * companions or purely logical pets; combines naturally with any movement type.
 */
public class InvisiblePetRepresentation implements PetRepresentationController {

    private final NamespacedKey petIdKey;
    private final NamespacedKey ownerIdKey;
    private final NamespacedKey definitionIdKey;
    private final NamespacedKey schemaVersionKey;

    public InvisiblePetRepresentation(JavaPlugin plugin) {
        Objects.requireNonNull(plugin, "plugin null olamaz.");
        this.petIdKey = new NamespacedKey(plugin, "pet_id");
        this.ownerIdKey = new NamespacedKey(plugin, "owner_id");
        this.definitionIdKey = new NamespacedKey(plugin, "definition_id");
        this.schemaVersionKey = new NamespacedKey(plugin, "schema_version");
    }

    @Override
    public Entity spawn(PetInstance pet, PetDefinition definition, Player owner) {
        Entity marker = owner.getWorld().spawnEntity(owner.getLocation(), EntityType.MARKER);
        marker.setInvulnerable(true);
        marker.setSilent(true);
        marker.setPersistent(false);
        DisplayPetSupport.tagPet(marker, petIdKey, ownerIdKey, definitionIdKey, schemaVersionKey,
                pet.petId(), pet.ownerId(), pet.definitionId(), 1);
        return marker;
    }

    @Override
    public void updateVisual(Entity primaryEntity, PetInstance pet, PetDefinition definition) {
        // no visible body to update
    }

    @Override
    public void remove(Entity primaryEntity) {
        if (primaryEntity != null && primaryEntity.isValid()) {
            primaryEntity.remove();
        }
    }

    @Override
    public boolean isValid(Entity primaryEntity) {
        return primaryEntity != null && primaryEntity.isValid();
    }
}

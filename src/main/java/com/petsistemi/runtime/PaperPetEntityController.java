package com.petsistemi.runtime;

import com.petsistemi.domain.PetDefinition;
import com.petsistemi.domain.PetInstance;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.*;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class PaperPetEntityController implements PetEntityController {

    private final JavaPlugin plugin;
    private final NamespacedKey petIdKey;
    private final NamespacedKey ownerIdKey;
    private final NamespacedKey definitionIdKey;
    private final NamespacedKey schemaVersionKey;

    public PaperPetEntityController(JavaPlugin plugin) {
        this.plugin = plugin;
        this.petIdKey = new NamespacedKey(plugin, "pet_id");
        this.ownerIdKey = new NamespacedKey(plugin, "owner_id");
        this.definitionIdKey = new NamespacedKey(plugin, "definition_id");
        this.schemaVersionKey = new NamespacedKey(plugin, "schema_version");
    }

    @Override
    public Entity spawn(PetInstance pet, PetDefinition definition, Player owner) {
        Entity entity = owner.getWorld().spawnEntity(owner.getLocation(), definition.entityType());

        // Vanilla settings from definition
        entity.setInvulnerable(definition.invulnerable());
        entity.setSilent(definition.silent());
        entity.setGravity(definition.gravity());
        entity.setGlowing(definition.glowing());
        entity.setPersistent(false); // Do not save pet entities in world chunks!

        if (entity instanceof Ageable ageable) {
            if (definition.baby()) {
                ageable.setBaby();
            } else {
                ageable.setAdult();
            }
            ageable.setAgeLock(true);
        }

        if (entity instanceof Tameable tameable) {
            tameable.setTamed(true);
            tameable.setOwner(owner);
        }

        if (entity instanceof LivingEntity living) {
            living.setRemoveWhenFarAway(false);
            living.setCanPickupItems(false);
        }

        // PDC tags
        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        pdc.set(petIdKey, PersistentDataType.STRING, pet.petId().toString());
        pdc.set(ownerIdKey, PersistentDataType.STRING, pet.ownerId().toString());
        pdc.set(definitionIdKey, PersistentDataType.STRING, pet.definitionId());
        pdc.set(schemaVersionKey, PersistentDataType.INTEGER, 1);

        // Nameplate rendering
        updateName(entity, pet, definition);

        return entity;
    }

    @Override
    public void remove(Entity entity) {
        if (entity != null && entity.isValid()) {
            entity.remove();
        }
    }

    @Override
    public void updateName(Entity entity, PetInstance pet, PetDefinition definition) {
        if (!definition.nameplateEnabled()) {
            entity.setCustomNameVisible(false);
            return;
        }

        String petName = pet.customName() != null ? pet.customName() : definition.displayName();
        
        List<String> lines = definition.nameplateFormat();
        List<Component> components = new ArrayList<>();
        
        for (String line : lines) {
            String processed = line.replace("{pet_name}", petName)
                                  .replace("{level}", String.valueOf(pet.level()));
            components.add(MiniMessage.miniMessage().deserialize(processed));
        }

        // Join multiple nameplate components with space/separator since vanilla nameplate is single line
        Component joined = Component.empty();
        for (int i = 0; i < components.size(); i++) {
            joined = joined.append(components.get(i));
            if (i < components.size() - 1) {
                joined = joined.append(Component.text(" - "));
            }
        }

        entity.customName(joined);
        entity.setCustomNameVisible(true);
    }

    @Override
    public boolean isValid(Entity entity) {
        return entity != null && entity.isValid();
    }
}

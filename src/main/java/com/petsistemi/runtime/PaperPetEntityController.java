package com.petsistemi.runtime;

import com.petsistemi.domain.PetDefinition;
import com.petsistemi.domain.PetInstance;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.*;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

public class PaperPetEntityController implements PetEntityController, PetRepresentationController {

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
        EntityType type;
        try {
            type = EntityType.valueOf(definition.entityType().toUpperCase(java.util.Locale.ROOT));
        } catch (Exception e) {
            throw new IllegalArgumentException("Geçersiz veya desteklenmeyen EntityType '" + definition.entityType() + "' pet tanımında (" + definition.id() + ") tanımlanmış!");
        }
        Entity entity = owner.getWorld().spawnEntity(owner.getLocation(), type);

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

        // Entry spawn animation (PORTAL/particle & sound)
        if (definition != null && definition.spawnStyle() != null) {
            com.petsistemi.domain.PetSpawnStyleDefinition style = definition.spawnStyle();
            if (style.entryParticle() != null) {
                try {
                    org.bukkit.Particle p = org.bukkit.Particle.valueOf(style.entryParticle().toUpperCase(java.util.Locale.ROOT));
                    owner.getWorld().spawnParticle(p, owner.getLocation().add(0, 0.5, 0), Math.max(1, style.entryParticleCount()), 0.4, 0.5, 0.4, 0.05);
                } catch (Exception ignored) {}
            }
            if (style.entrySound() != null) {
                try {
                    org.bukkit.Sound s = org.bukkit.Sound.valueOf(style.entrySound().toUpperCase(java.util.Locale.ROOT));
                    owner.playSound(owner.getLocation(), s, 1.0f, 1.0f);
                } catch (Exception ignored) {}
            }
        }

        return entity;
    }

    @Override
    public void remove(Entity entity) {
        if (entity != null && entity.isValid()) {
            try {
                entity.getWorld().spawnParticle(org.bukkit.Particle.SMOKE_LARGE, entity.getLocation().add(0, 0.5, 0), 20, 0.3, 0.4, 0.3, 0.05);
                entity.getWorld().playSound(entity.getLocation(), org.bukkit.Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
            } catch (Exception ignored) {}
            entity.remove();
        }
    }

    @Override
    public void updateName(Entity entity, PetInstance pet, PetDefinition definition) {
        PetNameplateRenderer.updateName(entity, pet, definition);
    }

    @Override
    public void updateVisual(Entity primaryEntity, PetInstance pet, PetDefinition definition) {
        if (primaryEntity != null && primaryEntity.isValid()) {
            primaryEntity.setGlowing(definition.glowing());
            if (primaryEntity instanceof Ageable ageable) {
                if (definition.baby()) {
                    ageable.setBaby();
                } else {
                    ageable.setAdult();
                }
                ageable.setAgeLock(true);
            }
        }
        PetNameplateRenderer.updateName(primaryEntity, pet, definition);
    }

    @Override
    public void applyRestState(Entity primaryEntity, PetInstance pet, PetDefinition definition, boolean resting) {
        if (primaryEntity instanceof org.bukkit.entity.Sittable sittable && sittable.isSitting() != resting) {
            sittable.setSitting(resting);
        }
    }

    @Override
    public boolean isValid(Entity entity) {
        return entity != null && entity.isValid();
    }
}

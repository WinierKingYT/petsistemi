package com.petsistemi.runtime;

import com.petsistemi.domain.PetDefinition;
import com.petsistemi.domain.PetInstance;
import com.petsistemi.domain.PetRepresentationDefinition;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

/**
 * Particle representation: an invisible marker entity that emits a configured
 * particle aura around its owner. The pet is tracked normally (watchdog, restore)
 * but has no visible body; particles are emitted in {@link #tickVisual}.
 */
public class ParticlePetRepresentation implements PetRepresentationController {

    private static final Particle DEFAULT_PARTICLE = Particle.SOUL_FIRE_FLAME;

    private final NamespacedKey petIdKey;
    private final NamespacedKey ownerIdKey;
    private final NamespacedKey definitionIdKey;
    private final NamespacedKey schemaVersionKey;

    public ParticlePetRepresentation(JavaPlugin plugin) {
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
    public void tickVisual(Entity primaryEntity, PetInstance pet, PetDefinition definition, Player owner) {
        if (primaryEntity == null || !primaryEntity.isValid() || owner == null || !owner.isOnline()) {
            return;
        }
        PetRepresentationDefinition rep = definition.representationOrEntity();
        Particle particle = resolveParticle(rep.particleType());
        if (particle == null) {
            return;
        }
        int count = Math.max(1, rep.particleCount());
        double offset = rep.particleOffset() > 0.0 ? rep.particleOffset() : 0.3;
        double speed = rep.particleSpeed() > 0.0 ? rep.particleSpeed() : 0.02;

        // The aura is the pet's body: it must render wherever the movement controller
        // put the marker, otherwise movement.type/height are visually inert.
        primaryEntity.getWorld().spawnParticle(particle, primaryEntity.getLocation(),
                count, offset, offset, offset, speed);
    }

    static Particle resolveParticle(String raw) {
        if (raw == null || raw.isBlank()) {
            return DEFAULT_PARTICLE;
        }
        try {
            return Particle.valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
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

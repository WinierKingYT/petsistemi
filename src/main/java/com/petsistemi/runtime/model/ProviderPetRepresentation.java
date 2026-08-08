package com.petsistemi.runtime.model;

import com.petsistemi.api.model.PetModelHandle;
import com.petsistemi.api.model.PetModelProvider;
import com.petsistemi.config.RuntimeConfigurationSnapshot;
import com.petsistemi.domain.PetDefinition;
import com.petsistemi.domain.PetInstance;
import com.petsistemi.domain.PetVector3;
import com.petsistemi.runtime.LevelScalePolicy;
import com.petsistemi.runtime.PetNameplateRenderer;
import com.petsistemi.runtime.PetRepresentationController;
import com.petsistemi.runtime.animation.PetAnimationTransition;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Sittable;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/** Converts any {@link PetModelProvider} into the standard representation pipeline. */
public final class ProviderPetRepresentation implements PetRepresentationController {
    private final PetModelProvider provider;
    private final AtomicReference<RuntimeConfigurationSnapshot> configSnapshot;
    private final Map<UUID, PetModelHandle> handles = new HashMap<>();
    private final NamespacedKey petIdKey;
    private final NamespacedKey ownerIdKey;
    private final NamespacedKey definitionIdKey;
    private final NamespacedKey schemaVersionKey;

    public ProviderPetRepresentation(JavaPlugin plugin, PetModelProvider provider,
                                     AtomicReference<RuntimeConfigurationSnapshot> configSnapshot) {
        Objects.requireNonNull(plugin, "plugin null olamaz.");
        this.provider = Objects.requireNonNull(provider, "provider null olamaz.");
        this.configSnapshot = configSnapshot;
        this.petIdKey = new NamespacedKey(plugin, "pet_id");
        this.ownerIdKey = new NamespacedKey(plugin, "owner_id");
        this.definitionIdKey = new NamespacedKey(plugin, "definition_id");
        this.schemaVersionKey = new NamespacedKey(plugin, "schema_version");
    }

    @Override
    public Entity spawn(PetInstance pet, PetDefinition definition, Player owner) {
        PetModelHandle handle = Objects.requireNonNull(provider.spawn(pet, definition, owner),
                "Model provider spawn null handle döndürdü: " + provider.key());
        Entity entity = handle.entity();
        configureEntity(entity, definition);
        tag(entity, pet);
        if (entity instanceof Display display) applyDisplayScale(display, scale(pet, definition));
        PetNameplateRenderer.updateName(entity, pet, definition);
        handles.put(entity.getUniqueId(), handle);
        return entity;
    }

    @Override
    public void tickVisual(Entity primaryEntity, PetInstance pet, PetDefinition definition, Player owner) {
        PetModelHandle handle = handle(primaryEntity);
        if (handle != null) provider.tick(handle, pet, definition, owner);
    }

    @Override
    public void updateVisual(Entity primaryEntity, PetInstance pet, PetDefinition definition) {
        PetModelHandle handle = handle(primaryEntity);
        if (handle != null) provider.updateVisual(handle, pet, definition);
        configureEntity(primaryEntity, definition);
        if (primaryEntity instanceof Display display) applyDisplayScale(display, scale(pet, definition));
        PetNameplateRenderer.updateName(primaryEntity, pet, definition);
    }

    @Override
    public void applyAnimation(Entity primaryEntity, PetInstance pet, PetDefinition definition,
                               PetAnimationTransition transition) {
        PetModelHandle handle = handle(primaryEntity);
        if (handle != null) provider.applyAnimation(handle, transition);
        PetRepresentationController.super.applyAnimation(primaryEntity, pet, definition, transition);
    }

    @Override
    public void applyRestState(Entity primaryEntity, PetInstance pet, PetDefinition definition, boolean resting) {
        if (primaryEntity instanceof Sittable sittable && sittable.isSitting() != resting) {
            sittable.setSitting(resting);
        }
        if (primaryEntity instanceof Display display) {
            PetVector3 base = scale(pet, definition);
            double factor = resting ? 0.65 : 1.0;
            applyDisplayScale(display, new PetVector3(base.x() * factor, base.y() * factor, base.z() * factor));
        }
    }

    @Override
    public void remove(Entity primaryEntity) {
        PetModelHandle handle = primaryEntity != null ? handles.remove(primaryEntity.getUniqueId()) : null;
        try {
            if (handle != null) provider.remove(handle);
        } finally {
            if (primaryEntity != null && primaryEntity.isValid()) primaryEntity.remove();
        }
    }

    @Override
    public boolean isValid(Entity primaryEntity) {
        return primaryEntity != null && primaryEntity.isValid() && handles.containsKey(primaryEntity.getUniqueId());
    }

    private PetModelHandle handle(Entity entity) {
        return entity != null ? handles.get(entity.getUniqueId()) : null;
    }

    private PetVector3 scale(PetInstance pet, PetDefinition definition) {
        PetVector3 configured = definition != null && definition.representationOrEntity() != null
                ? definition.representationOrEntity().scale() : PetVector3.ONE;
        int level = pet != null ? pet.level() : 1;
        return LevelScalePolicy.fromSnapshot(configured, level, configSnapshot);
    }

    private static void configureEntity(Entity entity, PetDefinition definition) {
        if (entity == null || definition == null) return;
        var rep = definition.representationOrEntity();
        entity.setInvulnerable(rep.invulnerable());
        entity.setSilent(rep.silent());
        entity.setGravity(rep.gravity());
        entity.setGlowing(rep.glowing());
        entity.setPersistent(false);
        if (entity instanceof LivingEntity living) {
            living.setCollidable(false);
            living.setCanPickupItems(false);
            living.setRemoveWhenFarAway(false);
        }
        if (entity instanceof Display display) {
            display.setBillboard(Display.Billboard.CENTER);
            display.setDisplayWidth(0.4f);
            display.setDisplayHeight(0.4f);
        }
    }

    private void tag(Entity entity, PetInstance pet) {
        entity.getPersistentDataContainer().set(petIdKey, PersistentDataType.STRING, pet.petId().toString());
        entity.getPersistentDataContainer().set(ownerIdKey, PersistentDataType.STRING, pet.ownerId().toString());
        entity.getPersistentDataContainer().set(definitionIdKey, PersistentDataType.STRING, pet.definitionId());
        entity.getPersistentDataContainer().set(schemaVersionKey, PersistentDataType.INTEGER, 1);
    }

    private static void applyDisplayScale(Display display, PetVector3 scale) {
        display.setTransformation(new Transformation(new Vector3f(), new Quaternionf(),
                new Vector3f((float) scale.x(), (float) scale.y(), (float) scale.z()), new Quaternionf()));
    }
}

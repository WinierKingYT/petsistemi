package com.petsistemi.runtime;

import com.petsistemi.config.RuntimeConfigurationSnapshot;
import com.petsistemi.domain.PetDefinition;
import com.petsistemi.domain.PetInstance;
import com.petsistemi.domain.PetRepresentationDefinition;
import com.petsistemi.domain.PetVector3;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Multi-entity representation: a primary {@link ItemDisplay} plus a configurable
 * number of child displays (a swarm / formation pack). Children are tracked on the
 * runtime handle and cleaned up with the primary; their positions are driven by the
 * FORMATION movement controller.
 */
public class MultiEntityPetRepresentation implements PetRepresentationController {

    private static final Material DEFAULT_MATERIAL = Material.AMETHYST_SHARD;
    private static final int MAX_CHILDREN = 8;

    private final NamespacedKey petIdKey;
    private final NamespacedKey ownerIdKey;
    private final NamespacedKey definitionIdKey;
    private final NamespacedKey schemaVersionKey;
    private final AtomicReference<RuntimeConfigurationSnapshot> configSnapshot;

    public MultiEntityPetRepresentation(JavaPlugin plugin) {
        this(plugin, null);
    }

    public MultiEntityPetRepresentation(JavaPlugin plugin, AtomicReference<RuntimeConfigurationSnapshot> configSnapshot) {
        Objects.requireNonNull(plugin, "plugin null olamaz.");
        this.petIdKey = new NamespacedKey(plugin, "pet_id");
        this.ownerIdKey = new NamespacedKey(plugin, "owner_id");
        this.definitionIdKey = new NamespacedKey(plugin, "definition_id");
        this.schemaVersionKey = new NamespacedKey(plugin, "schema_version");
        this.configSnapshot = configSnapshot;
    }

    @Override
    public Entity spawn(PetInstance pet, PetDefinition definition, Player owner) {
        PetRepresentationDefinition rep = definition.representationOrEntity();

        Material material = resolveMaterial(rep.itemMaterial(), DEFAULT_MATERIAL);
        PetVector3 scale = LevelScalePolicy.fromSnapshot(rep.scale(), pet.level(), configSnapshot);
        ItemDisplay primary = spawnDisplay(owner, pet, material, rep.customModelData(),
                scale, rep.glowing(), 0);

        PetNameplateRenderer.updateName(primary, pet, definition);
        return primary;
    }

    @Override
    public List<Entity> spawnChildren(Entity primaryEntity, PetInstance pet, PetDefinition definition, Player owner) {
        PetRepresentationDefinition rep = definition.representationOrEntity();
        int childCount = Math.min(MAX_CHILDREN, Math.max(0, rep.childCount()));
        if (childCount == 0) {
            return List.of();
        }

        // Children inherit the primary's material when child-material is unset. Falling back
        // to the global default instead made a swarm render as one honeycomb surrounded by
        // three unrelated amethysts.
        Material childMaterial = resolveMaterial(
                rep.childMaterial() != null ? rep.childMaterial() : rep.itemMaterial(),
                DEFAULT_MATERIAL);
        PetVector3 scale = LevelScalePolicy.fromSnapshot(rep.scale(), pet.level(), configSnapshot);
        List<Entity> children = new ArrayList<>(childCount);
        for (int i = 1; i <= childCount; i++) {
            ItemDisplay child = spawnDisplay(owner, pet, childMaterial, rep.customModelData(), scale, rep.glowing(), i);
            children.add(child);
        }
        return children;
    }

    private ItemDisplay spawnDisplay(Player owner, PetInstance pet, Material material, Integer customModelData,
                                     PetVector3 scale, boolean glowing, int index) {
        ItemDisplay display = (ItemDisplay) owner.getWorld().spawnEntity(owner.getLocation(), EntityType.ITEM_DISPLAY);
        ItemStack stack = new ItemStack(material);
        if (customModelData != null) {
            stack.editMeta(meta -> meta.setCustomModelData(customModelData));
        }
        display.setItemStack(stack);
        DisplayPetSupport.applyBaseDisplaySettings(display, glowing);
        DisplayPetSupport.applyScale(display, scale);
        DisplayPetSupport.tagPet(display, petIdKey, ownerIdKey, definitionIdKey, schemaVersionKey,
                pet.petId(), pet.ownerId(), pet.definitionId(), 1);
        return display;
    }

    private static Material resolveMaterial(String raw, Material fallback) {
        if (raw == null) {
            return fallback;
        }
        Material parsed = Material.matchMaterial(raw);
        return parsed != null ? parsed : fallback;
    }

    @Override
    public void updateVisual(Entity primaryEntity, PetInstance pet, PetDefinition definition) {
        if (primaryEntity instanceof ItemDisplay display) {
            display.setGlowing(definition.representationOrEntity().glowing());
            PetVector3 scale = LevelScalePolicy.fromSnapshot(definition.representationOrEntity().scale(), pet.level(), configSnapshot);
            DisplayPetSupport.applyScale(display, scale);
        }
        PetNameplateRenderer.updateName(primaryEntity, pet, definition);
    }

    @Override
    public void applyRestState(Entity primaryEntity, PetInstance pet, PetDefinition definition, boolean resting) {
        if (primaryEntity instanceof ItemDisplay display) {
            PetVector3 scale = LevelScalePolicy.fromSnapshot(definition.representationOrEntity().scale(), pet.level(), configSnapshot);
            DisplayPetSupport.applyScale(display, ItemDisplayPetRepresentation.restScale(scale, resting));
        }
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

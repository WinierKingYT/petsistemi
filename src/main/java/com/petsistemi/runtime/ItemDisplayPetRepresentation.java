package com.petsistemi.runtime;

import com.petsistemi.config.RuntimeConfigurationSnapshot;
import com.petsistemi.domain.PetDefinition;
import com.petsistemi.domain.PetInstance;
import com.petsistemi.domain.PetRepresentationDefinition;
import com.petsistemi.domain.PetVector3;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Item-display representation: a collision-free, custom-model-capable visual
 * used by familiar/orbit/trail style pets. No mob AI, no pathfinding.
 */
public class ItemDisplayPetRepresentation implements PetRepresentationController {

    private static final Material DEFAULT_MATERIAL = Material.AMETHYST_SHARD;

    private final NamespacedKey petIdKey;
    private final NamespacedKey ownerIdKey;
    private final NamespacedKey definitionIdKey;
    private final NamespacedKey schemaVersionKey;
    private final AtomicReference<RuntimeConfigurationSnapshot> configSnapshot;

    public ItemDisplayPetRepresentation(JavaPlugin plugin) {
        this(plugin, null);
    }

    public ItemDisplayPetRepresentation(JavaPlugin plugin, AtomicReference<RuntimeConfigurationSnapshot> configSnapshot) {
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

        Material material = DEFAULT_MATERIAL;
        if (rep.itemMaterial() != null) {
            Material parsed = Material.matchMaterial(rep.itemMaterial());
            if (parsed != null) {
                material = parsed;
            }
        }

        ItemDisplay display = (ItemDisplay) owner.getWorld().spawnEntity(owner.getLocation(), EntityType.ITEM_DISPLAY);

        ItemStack stack = new ItemStack(material);
        if (rep.customModelData() != null) {
            stack.editMeta(meta -> meta.setCustomModelData(rep.customModelData()));
        }
        display.setItemStack(stack);

        PetVector3 scale = LevelScalePolicy.fromSnapshot(rep.scale(), pet.level(), configSnapshot);
        display.setTransformation(new Transformation(
                new Vector3f(0f, 0f, 0f),
                new Quaternionf(),
                new Vector3f((float) scale.x(), (float) scale.y(), (float) scale.z()),
                new Quaternionf()
        ));

        display.setBillboard(Display.Billboard.CENTER);
        display.setDisplayWidth(0.4f);
        display.setDisplayHeight(0.4f);
        display.setInvulnerable(true);
        display.setSilent(true);
        display.setGravity(false);
        display.setGlowing(rep.glowing());
        display.setPersistent(false);

        PersistentDataContainer pdc = display.getPersistentDataContainer();
        pdc.set(petIdKey, PersistentDataType.STRING, pet.petId().toString());
        pdc.set(ownerIdKey, PersistentDataType.STRING, pet.ownerId().toString());
        pdc.set(definitionIdKey, PersistentDataType.STRING, pet.definitionId());
        pdc.set(schemaVersionKey, PersistentDataType.INTEGER, 1);

        PetNameplateRenderer.updateName(display, pet, definition);
        return display;
    }

    @Override
    public void updateVisual(Entity primaryEntity, PetInstance pet, PetDefinition definition) {
        if (primaryEntity instanceof ItemDisplay display) {
            PetRepresentationDefinition rep = definition.representationOrEntity();
            Material material = DEFAULT_MATERIAL;
            if (rep.itemMaterial() != null) {
                Material parsed = Material.matchMaterial(rep.itemMaterial());
                if (parsed != null) {
                    material = parsed;
                }
            }
            ItemStack stack = new ItemStack(material);
            if (rep.customModelData() != null) {
                stack.editMeta(meta -> meta.setCustomModelData(rep.customModelData()));
            }
            display.setItemStack(stack);
            display.setGlowing(rep.glowing());
            PetVector3 scale = LevelScalePolicy.fromSnapshot(rep.scale(), pet.level(), configSnapshot);
            DisplayPetSupport.applyScale(display, scale);
        }
        PetNameplateRenderer.updateName(primaryEntity, pet, definition);
    }

    @Override
    public void applyRestState(Entity primaryEntity, PetInstance pet, PetDefinition definition, boolean resting) {
        if (primaryEntity instanceof ItemDisplay display) {
            PetVector3 scale = LevelScalePolicy.fromSnapshot(definition.representationOrEntity().scale(), pet.level(), configSnapshot);
            DisplayPetSupport.applyScale(display, restScale(scale, resting));
        }
    }

    static PetVector3 restScale(PetVector3 scale, boolean resting) {
        if (!resting) {
            return scale;
        }
        final double factor = 0.65;
        return new PetVector3(scale.x() * factor, scale.y() * factor, scale.z() * factor);
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

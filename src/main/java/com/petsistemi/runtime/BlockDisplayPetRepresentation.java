package com.petsistemi.runtime;

import com.petsistemi.config.RuntimeConfigurationSnapshot;
import com.petsistemi.domain.PetDefinition;
import com.petsistemi.domain.PetInstance;
import com.petsistemi.domain.PetRepresentationDefinition;
import com.petsistemi.domain.PetVector3;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Block-display representation: renders a Minecraft block as the pet's body
 * (e.g. a hovering crystal block). Collision-free, no mob AI.
 */
public class BlockDisplayPetRepresentation implements PetRepresentationController {

    private static final Material DEFAULT_BLOCK = Material.AMETHYST_BLOCK;

    private final NamespacedKey petIdKey;
    private final NamespacedKey ownerIdKey;
    private final NamespacedKey definitionIdKey;
    private final NamespacedKey schemaVersionKey;
    private final AtomicReference<RuntimeConfigurationSnapshot> configSnapshot;

    public BlockDisplayPetRepresentation(JavaPlugin plugin) {
        this(plugin, null);
    }

    public BlockDisplayPetRepresentation(JavaPlugin plugin, AtomicReference<RuntimeConfigurationSnapshot> configSnapshot) {
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

        Material block = DEFAULT_BLOCK;
        if (rep.itemMaterial() != null) {
            Material parsed = Material.matchMaterial(rep.itemMaterial());
            if (parsed != null) {
                block = parsed;
            }
        }

        BlockDisplay display = (BlockDisplay) owner.getWorld().spawnEntity(owner.getLocation(), EntityType.BLOCK_DISPLAY);
        display.setBlock(block.createBlockData());
        DisplayPetSupport.applyBaseDisplaySettings(display, rep.glowing());
        DisplayPetSupport.applyScale(display, LevelScalePolicy.fromSnapshot(rep.scale(), pet.level(), configSnapshot));
        DisplayPetSupport.tagPet(display, petIdKey, ownerIdKey, definitionIdKey, schemaVersionKey,
                pet.petId(), pet.ownerId(), pet.definitionId(), 1);

        PetNameplateRenderer.updateName(display, pet, definition);
        return display;
    }

    @Override
    public void updateVisual(Entity primaryEntity, PetInstance pet, PetDefinition definition) {
        if (primaryEntity instanceof BlockDisplay display) {
            PetRepresentationDefinition rep = definition.representationOrEntity();
            Material block = DEFAULT_BLOCK;
            if (rep.itemMaterial() != null) {
                Material parsed = Material.matchMaterial(rep.itemMaterial());
                if (parsed != null) {
                    block = parsed;
                }
            }
            if (display.getBlock().getMaterial() != block) {
                display.setBlock(block.createBlockData());
            }
            display.setGlowing(rep.glowing());
            PetVector3 scale = LevelScalePolicy.fromSnapshot(rep.scale(), pet.level(), configSnapshot);
            DisplayPetSupport.applyScale(display, scale);
        }
        PetNameplateRenderer.updateName(primaryEntity, pet, definition);
    }

    @Override
    public void applyRestState(Entity primaryEntity, PetInstance pet, PetDefinition definition, boolean resting) {
        if (primaryEntity instanceof BlockDisplay display) {
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

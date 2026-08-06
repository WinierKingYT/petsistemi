package com.petsistemi.runtime;

import com.petsistemi.config.RuntimeConfigurationSnapshot;
import com.petsistemi.domain.PetDefinition;
import com.petsistemi.domain.PetInstance;
import com.petsistemi.domain.PetRepresentationDefinition;
import com.petsistemi.domain.PetVector3;
import com.petsistemi.util.LegacyColorTranslator;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Color;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Text-display representation: the pet is a floating billboard showing its own
 * name (MiniMessage colors supported). No nameplate is drawn on top of it.
 */
public class TextDisplayPetRepresentation implements PetRepresentationController {

    private final NamespacedKey petIdKey;
    private final NamespacedKey ownerIdKey;
    private final NamespacedKey definitionIdKey;
    private final NamespacedKey schemaVersionKey;
    private final AtomicReference<RuntimeConfigurationSnapshot> configSnapshot;

    public TextDisplayPetRepresentation(JavaPlugin plugin) {
        this(plugin, null);
    }

    public TextDisplayPetRepresentation(JavaPlugin plugin, AtomicReference<RuntimeConfigurationSnapshot> configSnapshot) {
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

        TextDisplay display = (TextDisplay) owner.getWorld().spawnEntity(owner.getLocation(), EntityType.TEXT_DISPLAY);
        display.text(textFor(pet, definition));
        display.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
        display.setTextOpacity((byte) 200);
        display.setShadowed(false);
        display.setSeeThrough(false);
        DisplayPetSupport.applyBaseDisplaySettings(display, rep.glowing());
        DisplayPetSupport.applyScale(display, LevelScalePolicy.fromSnapshot(rep.scale(), pet.level(), configSnapshot));
        DisplayPetSupport.tagPet(display, petIdKey, ownerIdKey, definitionIdKey, schemaVersionKey,
                pet.petId(), pet.ownerId(), pet.definitionId(), 1);

        return display;
    }

    static Component textFor(PetInstance pet, PetDefinition definition) {
        String petName = pet.customName() != null ? pet.customName() : definition.displayName();
        String petNameMini = LegacyColorTranslator.toMiniMessageString(petName);
        return MiniMessage.miniMessage().deserialize(petNameMini);
    }

    @Override
    public void updateVisual(Entity primaryEntity, PetInstance pet, PetDefinition definition) {
        if (primaryEntity instanceof TextDisplay display) {
            display.text(textFor(pet, definition));
            display.setGlowing(definition.representationOrEntity().glowing());
            PetVector3 scale = LevelScalePolicy.fromSnapshot(definition.representationOrEntity().scale(), pet.level(), configSnapshot);
            DisplayPetSupport.applyScale(display, scale);
        }
    }

    @Override
    public void applyRestState(Entity primaryEntity, PetInstance pet, PetDefinition definition, boolean resting) {
        if (primaryEntity instanceof TextDisplay display) {
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

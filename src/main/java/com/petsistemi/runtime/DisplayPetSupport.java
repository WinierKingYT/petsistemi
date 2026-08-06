package com.petsistemi.runtime;

import com.petsistemi.domain.PetVector3;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.UUID;

/** Shared display-entity setup used by item/block/text display representations. */
final class DisplayPetSupport {

    private DisplayPetSupport() {}

    static void applyBaseDisplaySettings(Display display, boolean glowing) {
        display.setBillboard(Display.Billboard.CENTER);
        display.setDisplayWidth(0.4f);
        display.setDisplayHeight(0.4f);
        display.setInvulnerable(true);
        display.setSilent(true);
        display.setGravity(false);
        display.setGlowing(glowing);
        display.setPersistent(false);
    }

    static void applyScale(Display display, PetVector3 scale) {
        PetVector3 s = scale != null ? scale : PetVector3.ONE;
        display.setTransformation(new Transformation(
                new Vector3f(0f, 0f, 0f),
                new Quaternionf(),
                new Vector3f((float) s.x(), (float) s.y(), (float) s.z()),
                new Quaternionf()
        ));
    }

    static void tagPet(Entity entity, NamespacedKey petIdKey, NamespacedKey ownerIdKey,
                       NamespacedKey definitionIdKey, NamespacedKey schemaVersionKey,
                       UUID petId, UUID ownerId, String definitionId, int schemaVersion) {
        entity.getPersistentDataContainer().set(petIdKey, PersistentDataType.STRING, petId.toString());
        entity.getPersistentDataContainer().set(ownerIdKey, PersistentDataType.STRING, ownerId.toString());
        entity.getPersistentDataContainer().set(definitionIdKey, PersistentDataType.STRING, definitionId);
        entity.getPersistentDataContainer().set(schemaVersionKey, PersistentDataType.INTEGER, schemaVersion);
    }
}

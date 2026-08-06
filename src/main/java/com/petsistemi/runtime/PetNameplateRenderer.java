package com.petsistemi.runtime;

import com.petsistemi.domain.PetDefinition;
import com.petsistemi.domain.PetInstance;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Entity;

import java.util.ArrayList;
import java.util.List;

/** Shared nameplate rendering for every representation type (works on any Entity). */
public final class PetNameplateRenderer {

    private PetNameplateRenderer() {}

    public static void updateName(Entity entity, PetInstance pet, PetDefinition definition) {
        if (entity == null) return;
        if (!definition.nameplateEnabled()) {
            entity.setCustomNameVisible(false);
            return;
        }

        String petName = pet.customName() != null ? pet.customName() : definition.displayName();
        String petNameMini = com.petsistemi.util.LegacyColorTranslator.toMiniMessageString(petName);

        List<String> lines = definition.nameplateFormat();
        List<Component> components = new ArrayList<>();

        for (String line : lines) {
            String processed = line.replace("{pet_name}", petNameMini)
                                  .replace("{level}", String.valueOf(pet.level()));
            components.add(MiniMessage.miniMessage().deserialize(processed));
        }

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
}

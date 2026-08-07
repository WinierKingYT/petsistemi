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

    /**
     * Resolves the pet's name as MiniMessage markup, ready to be embedded in a template.
     *
     * <p>The two name sources are <b>not</b> equally trusted. A player-chosen
     * {@code customName} is escaped, so a pet name can never inject MiniMessage tags
     * (colors, gradients, or click/hover events) into a nameplate. The definition's
     * {@code display-name} comes from an admin-authored {@code pets/*.yml} and is
     * documented as MiniMessage-capable, so its markup is passed through; legacy
     * {@code &}/{@code §} codes are still translated for older definition files.</p>
     */
    public static String nameMarkup(PetInstance pet, PetDefinition definition) {
        String customName = pet.customName();
        if (customName != null) {
            return com.petsistemi.util.LegacyColorTranslator.toMiniMessageString(customName);
        }
        String displayName = definition.displayName();
        if (displayName == null) {
            return "";
        }
        return com.petsistemi.util.LegacyColorTranslator.hasCodes(displayName)
                ? com.petsistemi.util.LegacyColorTranslator.toMiniMessageString(displayName)
                : displayName;
    }

    public static void updateName(Entity entity, PetInstance pet, PetDefinition definition) {
        if (entity == null) return;
        if (!definition.nameplateEnabled()) {
            entity.setCustomNameVisible(false);
            return;
        }

        String petNameMini = nameMarkup(pet, definition);

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

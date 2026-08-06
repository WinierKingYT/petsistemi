package com.petsistemi.gui;

import com.petsistemi.domain.PetDefinition;
import org.bukkit.Material;

/** Resolves the inventory icon shown for a pet in the list and inspect menus. */
final class PetMenuIcons {

    private PetMenuIcons() {}

    /**
     * Uses the definition's {@code gui-material} when it names a real item, otherwise falls
     * back to the built-in icons for the legacy pets and finally to a generic bone.
     */
    static Material resolve(PetDefinition definition, String definitionId) {
        if (definition != null && definition.guiMaterial() != null) {
            Material configured = Material.matchMaterial(definition.guiMaterial());
            if (configured != null && configured.isItem()) {
                return configured;
            }
        }
        return switch (definitionId != null ? definitionId.toLowerCase() : "") {
            case "wolf" -> Material.WOLF_SPAWN_EGG;
            case "cat" -> Material.CAT_SPAWN_EGG;
            case "allay" -> Material.ALLAY_SPAWN_EGG;
            default -> Material.BONE;
        };
    }
}

package com.petsistemi.gui;

import com.petsistemi.domain.PetDefinition;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PetMenuIconsTest {

    private static PetDefinition definition(String id, String guiMaterial) {
        return new PetDefinition(id, id, List.of(), "WOLF",
                false, false, true, false, true, true, 100, true, List.of("{pet_name}"),
                null, null, null, null, null, null, guiMaterial, null);
    }

    @Test
    void guiMaterialFromDefinitionWins() {
        assertEquals(Material.AMETHYST_SHARD,
                PetMenuIcons.resolve(definition("arcane_crystal", "AMETHYST_SHARD"), "arcane_crystal"));
    }

    @Test
    void unknownGuiMaterialFallsBackInsteadOfThrowing() {
        assertEquals(Material.BONE,
                PetMenuIcons.resolve(definition("mystery", "NOT_A_MATERIAL"), "mystery"));
    }

    @Test
    void nonItemGuiMaterialFallsBack() {
        // WATER is a valid Material but has no item form, so it cannot fill an inventory slot.
        assertEquals(Material.BONE, PetMenuIcons.resolve(definition("splashy", "WATER"), "splashy"));
    }

    @Test
    void legacyPetsKeepTheirBuiltInIconsWithoutGuiMaterial() {
        assertEquals(Material.WOLF_SPAWN_EGG, PetMenuIcons.resolve(definition("wolf", null), "wolf"));
        assertEquals(Material.CAT_SPAWN_EGG, PetMenuIcons.resolve(definition("cat", null), "cat"));
        assertEquals(Material.ALLAY_SPAWN_EGG, PetMenuIcons.resolve(definition("allay", null), "allay"));
    }

    @Test
    void missingDefinitionStillResolvesAnIcon() {
        assertEquals(Material.WOLF_SPAWN_EGG, PetMenuIcons.resolve(null, "wolf"));
        assertEquals(Material.BONE, PetMenuIcons.resolve(null, null));
    }
}

package com.petsistemi.domain;

import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link PetDefinition} has 30 components, most optional. Positional construction made a
 * misplaced argument bind the wrong field silently; the builder names each one.
 */
class PetDefinitionBuilderTest {

    @Test
    void builderRequiresOnlyIdAndDisplayName() {
        PetDefinition def = PetDefinition.builder("wolf", "Kurt Dostu").build();

        assertEquals("wolf", def.id());
        assertEquals("Kurt Dostu", def.displayName());
    }

    /** Defaults must match the legacy convenience constructors so behaviour does not shift. */
    @Test
    void unsetFieldsUseTheEstablishedDefaults() {
        PetDefinition def = PetDefinition.builder("wolf", "Kurt").build();

        assertEquals("WOLF", def.entityType());
        assertTrue(def.invulnerable(), "petler varsayılan olarak hasar almaz");
        assertTrue(def.gravity());
        assertTrue(def.progressionEnabled());
        assertEquals(100, def.maxLevel());
        assertTrue(def.nameplateEnabled());
        assertNotNull(def.nameplateFormat());
        assertNull(def.representation());
        assertNull(def.movement());
    }

    @Test
    void namedSettersLandOnTheMatchingComponent() {
        PetDefinition def = PetDefinition.builder("orb", "Orb")
                .entityType("ALLAY")
                .baby(true)
                .glowing(true)
                .silent(true)
                .maxLevel(50)
                .guiMaterial("END_CRYSTAL")
                .permission("companionpets.pet.orb")
                .personality(PetPersonalityType.CURIOUS)
                .build();

        assertEquals("ALLAY", def.entityType());
        assertTrue(def.baby());
        assertTrue(def.glowing());
        assertTrue(def.silent());
        assertEquals(50, def.maxLevel());
        assertEquals("END_CRYSTAL", def.guiMaterial());
        assertEquals("companionpets.pet.orb", def.permission());
        assertEquals(PetPersonalityType.CURIOUS, def.personality());
    }

    @Test
    void toBuilderRoundTripsEveryComponent() {
        PetDefinition original = PetDefinition.builder("cat", "Kedi")
                .description(List.of("satır"))
                .entityType("CAT")
                .baby(true).glowing(true).invulnerable(false).silent(true).gravity(false)
                .progressionEnabled(false).maxLevel(7)
                .nameplateEnabled(false).nameplateFormat(List.of("<gold>{pet_name}</gold>"))
                .guiMaterial("CAT_SPAWN_EGG")
                .permission("companionpets.pet.cat")
                .personality(PetPersonalityType.SHY)
                .build();

        assertEquals(original, original.toBuilder().build(), "kopya birebir aynı olmalı");
    }

    /**
     * Guards the failure mode this replaced: a transformed definition silently losing a
     * component that was added to the record later.
     */
    @Test
    void transformOnlyReplacesTheRepresentation() {
        PetDefinition original = PetDefinition.builder("wisplight", "Wisplight")
                .representation(new PetRepresentationDefinition(RuntimeRepresentationType.ITEM_DISPLAY,
                        "WOLF", false, false, true, false, true, "GLOWSTONE_DUST", null, PetVector3.ONE))
                .guiMaterial("GLOWSTONE_DUST")
                .permission("companionpets.pet.wisplight")
                .personality(PetPersonalityType.CURIOUS)
                .maxLevel(42)
                .build();

        PetDefinition transformed = original.withTransformApplied(new PetTransformDefinition(
                new PetTransformCondition(null, null, null, PetTimeOfDay.NIGHT, null),
                new PetVisualOverride("SOUL_LANTERN", null, null, null, null, null, null, null)));

        assertEquals("SOUL_LANTERN", transformed.representation().itemMaterial(), "görsel değişmeli");
        assertEquals(original.guiMaterial(), transformed.guiMaterial());
        assertEquals(original.permission(), transformed.permission());
        assertEquals(original.personality(), transformed.personality());
        assertEquals(original.maxLevel(), transformed.maxLevel());
        assertEquals(original.id(), transformed.id());
    }

    @Test
    void aTransformWithNothingToApplyReturnsTheSameInstance() {
        PetDefinition original = PetDefinition.builder("wolf", "Kurt").build();

        assertSame(original, original.withTransformApplied(null));
    }

    /**
     * Every record component needs a builder setter, otherwise a newly added field is
     * unreachable through the builder and quietly defaults to null everywhere.
     */
    @Test
    void builderCoversEveryRecordComponent() {
        List<String> missing = new java.util.ArrayList<>();
        for (RecordComponent component : PetDefinition.class.getRecordComponents()) {
            if (component.getName().equals("id") || component.getName().equals("displayName")) {
                continue; // supplied to builder(...)
            }
            boolean hasSetter = java.util.Arrays.stream(PetDefinition.Builder.class.getMethods())
                    .anyMatch(m -> m.getName().equals(component.getName()) && m.getParameterCount() == 1);
            if (!hasSetter) {
                missing.add(component.getName());
            }
        }

        assertTrue(missing.isEmpty(), () -> "Builder'da karşılığı olmayan bileşenler: " + missing);
    }
}

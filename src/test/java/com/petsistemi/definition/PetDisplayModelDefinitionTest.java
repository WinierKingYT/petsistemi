package com.petsistemi.definition;

import com.petsistemi.domain.RuntimeRepresentationType;
import com.petsistemi.domain.animation.PetAnimationState;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PetDisplayModelDefinitionTest {

    @Test
    void parsesDisplayAliasesSkeletonAndAnimationChannels() throws Exception {
        PetDefinitionYamlParser.Parsed parsed = parse("""
                display-name: Mechanical Bird
                representation:
                  type: DISPLAY_MODEL
                  root: body
                  parts:
                    left-wing:
                      parent: body
                      type: ITEM
                      material: FEATHER
                      model-data: 1302
                      transform:
                        translation: [0.6, 0.1, 0.0]
                        scale: [0.7, 0.7, 0.7]
                    body:
                      type: ITEM
                      material: IRON_NUGGET
                      model-data: 1301
                  animations:
                    IDLE:
                      duration-ticks: 20
                      loop: true
                      bones:
                        left-wing:
                          - tick: 0
                            rotation: [0.0, 0.0, -20.0]
                          - tick: 10
                            rotation: [0.0, 0.0, 20.0]
                          - tick: 20
                            rotation: [0.0, 0.0, -20.0]
                """);

        assertTrue(parsed.errors().isEmpty(), () -> parsed.errors().toString());
        var representation = parsed.definition().representation();
        assertEquals(RuntimeRepresentationType.DISPLAY_MODEL, representation.type());
        assertNotNull(representation.displayModel());
        assertEquals("body", representation.displayModel().skeleton().rootId());
        assertEquals(RuntimeRepresentationType.ITEM_DISPLAY,
                representation.displayModel().skeleton().find("left-wing").orElseThrow().representation().type());
        assertEquals(3, representation.displayModel().animations().get(PetAnimationState.IDLE)
                .channels().get("left-wing").size());
        assertTrue(PetDefinitionValidator.validate(parsed.definition(), 1).isEmpty());
    }

    @Test
    void validatorRejectsNonDisplayPartsAndUnknownAnimationBones() throws Exception {
        PetDefinitionYamlParser.Parsed parsed = parse("""
                display-name: Broken Model
                representation:
                  type: DISPLAY_MODEL
                  root: body
                  parts:
                    body:
                      type: ENTITY
                      entity-type: WOLF
                  animations:
                    IDLE:
                      duration-ticks: 10
                      bones:
                        missing:
                          - tick: 0
                            scale: [1.0, 1.0, 1.0]
                """);

        var errors = PetDefinitionValidator.validate(parsed.definition(), 1);
        assertTrue(errors.stream().anyMatch(error -> error.contains("yalnızca ITEM/BLOCK/TEXT")));
        assertTrue(errors.stream().anyMatch(error -> error.contains("bilinmeyen bone")));
    }

    private static PetDefinitionYamlParser.Parsed parse(String source) throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(source);
        return PetDefinitionYamlParser.parse("mechanical_bird", yaml);
    }
}

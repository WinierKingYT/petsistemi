package com.petsistemi.definition;

import com.petsistemi.domain.RuntimeRepresentationType;
import com.petsistemi.domain.visual.PetProceduralShape;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PetProceduralDefinitionTest {

    @Test
    void parsesPersistentDisplayShapeAndContent() throws Exception {
        PetDefinitionYamlParser.Parsed parsed = parse("""
                display-name: Arcane Galaxy
                representation:
                  type: PROCEDURAL
                  shape: CONSTELLATION
                  points: 16
                  radius: 1.2
                  height: 1.0
                  rotation-speed: 1.5
                  pulse-amplitude: 0.15
                  pulse-speed: 4.0
                  update-interval-ticks: 2
                  content:
                    type: ITEM
                    material: AMETHYST_SHARD
                    model-data: 15101
                    scale: [0.2, 0.2, 0.2]
                """);

        assertTrue(parsed.errors().isEmpty(), () -> parsed.errors().toString());
        var representation = parsed.definition().representation();
        assertEquals(RuntimeRepresentationType.PROCEDURAL, representation.type());
        assertEquals(PetProceduralShape.CONSTELLATION, representation.procedural().shape());
        assertEquals(16, representation.procedural().points());
        assertEquals(RuntimeRepresentationType.ITEM_DISPLAY, representation.procedural().content().type());
        assertEquals("AMETHYST_SHARD", representation.procedural().content().itemMaterial());
        assertTrue(PetDefinitionValidator.validate(parsed.definition(), 1).isEmpty());
    }

    @Test
    void rejectsTooManyNodesAndNonDisplayContent() throws Exception {
        PetDefinitionYamlParser.Parsed tooMany = parse("""
                display-name: Dense Galaxy
                representation:
                  type: PROCEDURAL
                  shape: RING
                  points: 33
                  content:
                    type: ITEM
                    material: AMETHYST_SHARD
                """);
        assertTrue(tooMany.errors().stream().anyMatch(error -> error.contains("3-32")),
                () -> tooMany.errors().toString());

        PetDefinitionYamlParser.Parsed entityContent = parse("""
                display-name: Invalid Galaxy
                representation:
                  type: PROCEDURAL
                  shape: RING
                  points: 8
                  content:
                    type: ENTITY
                    entity-type: WOLF
                """);
        assertTrue(PetDefinitionValidator.validate(entityContent.definition(), 1).stream()
                .anyMatch(error -> error.contains("yalnızca ITEM/BLOCK/TEXT")));
    }

    private static PetDefinitionYamlParser.Parsed parse(String source) throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(source);
        return PetDefinitionYamlParser.parse("arcane_galaxy", yaml);
    }
}

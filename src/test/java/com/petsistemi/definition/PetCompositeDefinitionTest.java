package com.petsistemi.definition;

import com.petsistemi.domain.PetDefinition;
import com.petsistemi.domain.RuntimeRepresentationType;
import com.petsistemi.domain.visual.PetVisualGraphDefinition;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PetCompositeDefinitionTest {

    @Test
    void parsesAndValidatesNamedCompositeComponentsInParentFirstOrder() throws Exception {
        PetDefinitionYamlParser.Parsed parsed = parse("""
                schema-version: 1
                display-name: Fire Familiar
                representation:
                  type: COMPOSITE
                  root: body
                  components:
                    aura:
                      parent: body
                      type: PARTICLE
                      particle-type: FLAME
                      particle-count: 6
                      transform:
                        translation: [0.0, 0.8, 0.0]
                        scale: [0.7, 0.7, 0.7]
                    body:
                      type: ITEM_DISPLAY
                      item-material: BLAZE_POWDER
                      custom-model-data: 1201
                movement:
                  type: FLYING_FOLLOW
                """);

        assertTrue(parsed.errors().isEmpty(), () -> parsed.errors().toString());
        PetDefinition definition = parsed.definition();
        assertNotNull(definition);
        assertEquals(RuntimeRepresentationType.COMPOSITE, definition.representation().type());
        PetVisualGraphDefinition graph = definition.representation().visualGraph();
        assertNotNull(graph);
        assertEquals(List.of("body", "aura"), graph.topologicalNodes().stream().map(node -> node.id()).toList());
        assertEquals(0.8, graph.find("aura").orElseThrow().transform().translation().y());
        assertEquals(0.7, graph.find("aura").orElseThrow().transform().scale().x());
        assertTrue(PetDefinitionValidator.validate(definition, 1).isEmpty());
    }

    @Test
    void rejectsMissingParentsAndNestedCompositeComponents() throws Exception {
        PetDefinitionYamlParser.Parsed missingParent = parse("""
                display-name: Broken
                representation:
                  type: COMPOSITE
                  root: body
                  components:
                    body:
                      type: ITEM_DISPLAY
                      item-material: PAPER
                    crown:
                      parent: missing
                      type: ITEM_DISPLAY
                      item-material: GOLD_NUGGET
                """);
        assertTrue(missingParent.errors().stream().anyMatch(error -> error.contains("parent node bulunamadı")));

        PetDefinitionYamlParser.Parsed nested = parse("""
                display-name: Nested
                representation:
                  type: COMPOSITE
                  root: body
                  components:
                    body:
                      type: ITEM_DISPLAY
                      item-material: PAPER
                    nested:
                      parent: body
                      type: COMPOSITE
                """);
        List<String> errors = PetDefinitionValidator.validate(nested.definition(), 1);
        assertTrue(errors.stream().anyMatch(error -> error.contains("iç içe COMPOSITE")), () -> errors.toString());
    }

    private static PetDefinitionYamlParser.Parsed parse(String source) throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(source);
        return PetDefinitionYamlParser.parse("fire_familiar", yaml);
    }
}

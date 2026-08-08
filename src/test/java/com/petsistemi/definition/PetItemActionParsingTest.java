package com.petsistemi.definition;

import be.seeseemelk.mockbukkit.MockBukkit;
import com.petsistemi.domain.item.PetItemActionDefinition;
import com.petsistemi.runtime.item.BuiltInPetItemActions;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PetItemActionParsingTest {

    @BeforeAll static void setUp() { MockBukkit.mock(); }
    @AfterAll static void tearDown() { MockBukkit.unmock(); }

    @Test
    void parsesNamespacedFeedActionAndMatcher() {
        YamlConfiguration yaml = yaml("BONE", "petsistemi:gain_experience", "amount: 25");

        PetDefinitionYamlParser.Parsed parsed = PetDefinitionYamlParser.parse("wolf", yaml);

        assertTrue(parsed.errors().isEmpty(), parsed.errors().toString());
        assertNotNull(parsed.definition());
        PetItemActionDefinition action = parsed.definition().itemActions().get(0);
        assertEquals("feed", action.id());
        assertEquals("BONE", action.material());
        assertEquals(2, action.consumeAmount());
        assertEquals(3, action.cooldownSeconds());
        assertEquals(5, action.minimumLevel());
        assertEquals(BuiltInPetItemActions.GAIN_EXPERIENCE, action.action());
        assertEquals(25, action.parameters().get("amount"));
        assertEquals(List.of(), PetDefinitionValidator.validate(parsed.definition(), 1));
    }

    @Test
    void validatorRejectsInvalidMatcherAndBuiltInParameters() {
        PetDefinitionYamlParser.Parsed parsed = PetDefinitionYamlParser.parse("wolf",
                yaml("NO_SUCH_ITEM", "petsistemi:gain_experience", "amount: 0"));

        List<String> errors = PetDefinitionValidator.validate(parsed.definition(), 1);

        assertTrue(errors.stream().anyMatch(error -> error.contains("item.material")));
        assertTrue(errors.stream().anyMatch(error -> error.contains("parameters.amount")));
    }

    @Test
    void validatorRequiresPersistentEvolutionTarget() {
        PetDefinitionYamlParser.Parsed parsed = PetDefinitionYamlParser.parse("wolf",
                yaml("AMETHYST_SHARD", "petsistemi:evolve_pet", "amount: 1"));

        List<String> errors = PetDefinitionValidator.validate(parsed.definition(), 1);

        assertTrue(errors.stream().anyMatch(error -> error.contains("parameters.target-id")));
    }

    private static YamlConfiguration yaml(String material, String action, String parameter) {
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.loadFromString("""
                    schema-version: 1
                    display-name: Wolf
                    gui-material: BONE
                    entity-type: WOLF
                    item-actions:
                      feed:
                        item:
                          material: %s
                        consume: 2
                        cooldown-seconds: 3
                        min-level: 5
                        action: %s
                        parameters:
                          %s
                    """.formatted(material, action, parameter));
        } catch (Exception e) {
            throw new AssertionError(e);
        }
        return yaml;
    }
}

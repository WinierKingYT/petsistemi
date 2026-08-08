package com.petsistemi.definition;

import com.petsistemi.domain.PetDefinition;
import com.petsistemi.domain.PetMountDefinition;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PetMountDefinitionTest {

    @Test
    void mountBlockParsesAllRuntimeControls() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("""
                display-name: Wolf
                entity-type: WOLF
                mount:
                  enabled: true
                  permission: pets.mount.wolf
                  speed-multiplier: 1.5
                  allow-fly: true
                """);

        PetDefinition definition = PetDefinitionYamlParser.parse("wolf", yaml).definition();

        assertNotNull(definition.mount());
        assertTrue(definition.mount().enabled());
        assertEquals("pets.mount.wolf", definition.mount().permission());
        assertEquals(1.5D, definition.mount().speedMultiplier());
        assertTrue(definition.mount().allowFly());
    }

    @Test
    void missingMountBlockKeepsLegacyGlobalFallback() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("display-name: Wolf\nentity-type: WOLF\n");

        assertNull(PetDefinitionYamlParser.parse("wolf", yaml).definition().mount());
    }

    @Test
    void unsafeSpeedMultiplierIsRejected() {
        PetDefinition definition = PetDefinition.builder("wolf", "Wolf")
                .mount(new PetMountDefinition(true, null, 4.0D, false)).build();

        List<String> errors = PetDefinitionValidator.validate(definition, 1);

        assertTrue(errors.stream().anyMatch(error -> error.contains("mount.speed-multiplier")));
    }
}

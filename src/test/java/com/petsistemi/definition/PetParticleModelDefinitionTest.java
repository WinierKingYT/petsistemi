package com.petsistemi.definition;

import com.petsistemi.domain.RuntimeRepresentationType;
import com.petsistemi.domain.visual.PetParticleShape;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PetParticleModelDefinitionTest {

    @Test
    void parsesMultipleProceduralShapes() throws Exception {
        PetDefinitionYamlParser.Parsed parsed = parse("""
                display-name: Astral Spirit
                representation:
                  type: PARTICLE_MODEL
                  update-interval-ticks: 2
                  model:
                    - shape: RING
                      particle: END_ROD
                      points: 16
                      radius: 0.8
                      offset: [0.0, 0.2, 0.0]
                      rotation-speed: 3.0
                    - shape: HELIX
                      particle: SOUL_FIRE_FLAME
                      points: 24
                      radius: 0.4
                      height: 1.2
                      rotation-speed: -2.0
                """);

        assertTrue(parsed.errors().isEmpty(), () -> parsed.errors().toString());
        var representation = parsed.definition().representation();
        assertEquals(RuntimeRepresentationType.PARTICLE_MODEL, representation.type());
        assertEquals(2, representation.particleModel().updateIntervalTicks());
        assertEquals(2, representation.particleModel().parts().size());
        assertEquals(PetParticleShape.RING, representation.particleModel().parts().get(0).shape());
        assertEquals(1.2, representation.particleModel().parts().get(1).height());
        assertTrue(PetDefinitionValidator.validate(parsed.definition(), 1).isEmpty());
    }

    @Test
    void rejectsExcessivePointBudgetAndDataParticles() throws Exception {
        PetDefinitionYamlParser.Parsed excessive = parse("""
                display-name: Too Dense
                representation:
                  type: PARTICLE_MODEL
                  model:
                    - shape: SPHERE
                      particle: END_ROD
                      points: 200
                      radius: 1.0
                    - shape: RING
                      particle: END_ROD
                      points: 100
                      radius: 1.0
                """);
        assertTrue(excessive.errors().stream().anyMatch(error -> error.contains("256")),
                () -> excessive.errors().toString());

        PetDefinitionYamlParser.Parsed dataParticle = parse("""
                display-name: Invalid Particle
                representation:
                  type: PARTICLE_MODEL
                  model:
                    - shape: RING
                      particle: BLOCK_CRACK
                      points: 12
                      radius: 1.0
                """);
        assertTrue(PetDefinitionValidator.validate(dataParticle.definition(), 1).stream()
                .anyMatch(error -> error.contains("data gerektiren")));
    }

    private static PetDefinitionYamlParser.Parsed parse(String source) throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(source);
        return PetDefinitionYamlParser.parse("astral_spirit", yaml);
    }
}

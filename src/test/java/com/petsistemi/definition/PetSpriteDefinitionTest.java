package com.petsistemi.definition;

import com.petsistemi.domain.RuntimeRepresentationType;
import com.petsistemi.domain.animation.PetAnimationState;
import com.petsistemi.domain.visual.PetSpriteBillboard;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PetSpriteDefinitionTest {

    @Test
    void parsesBillboardAndStateFrames() throws Exception {
        PetDefinitionYamlParser.Parsed parsed = parse("""
                display-name: Pixel Slime
                representation:
                  type: SPRITE
                  material: PAPER
                  billboard: VERTICAL
                  animations:
                    IDLE:
                      frame-ticks: 6
                      frames: [14101, 14102, 14103]
                    MOVING:
                      frame-ticks: 3
                      loop: false
                      frames: [14111, 14112]
                """);

        assertTrue(parsed.errors().isEmpty(), () -> parsed.errors().toString());
        var representation = parsed.definition().representation();
        assertEquals(RuntimeRepresentationType.SPRITE, representation.type());
        assertEquals(PetSpriteBillboard.VERTICAL, representation.sprite().billboard());
        assertEquals(6, representation.sprite().animations().get(PetAnimationState.IDLE).frameTicks());
        assertEquals(java.util.List.of(14111, 14112),
                representation.sprite().animations().get(PetAnimationState.MOVING).frames());
        assertTrue(PetDefinitionValidator.validate(parsed.definition(), 1).isEmpty());
    }

    @Test
    void customModelDataProvidesSingleFrameBackwardCompatibleSprite() throws Exception {
        PetDefinitionYamlParser.Parsed parsed = parse("""
                display-name: Static Icon
                representation:
                  type: SPRITE
                  material: PAPER
                  custom-model-data: 15001
                """);

        assertTrue(parsed.errors().isEmpty(), () -> parsed.errors().toString());
        assertEquals(java.util.List.of(15001), parsed.definition().representation().sprite()
                .animations().get(PetAnimationState.IDLE).frames());
    }

    @Test
    void rejectsEmptyOrNegativeFrames() throws Exception {
        PetDefinitionYamlParser.Parsed parsed = parse("""
                display-name: Broken Sprite
                representation:
                  type: SPRITE
                  material: PAPER
                  animations:
                    IDLE:
                      frame-ticks: 0
                      frames: [-1]
                """);

        assertTrue(parsed.errors().stream().anyMatch(error -> error.contains("frame-ticks")),
                () -> parsed.errors().toString());
    }

    private static PetDefinitionYamlParser.Parsed parse(String source) throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(source);
        return PetDefinitionYamlParser.parse("pixel_slime", yaml);
    }
}

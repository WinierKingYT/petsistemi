package com.petsistemi;

import com.petsistemi.definition.PetDefinitionValidator;
import com.petsistemi.definition.PetDefinitionYamlParser;
import com.petsistemi.domain.PetDefinition;
import com.petsistemi.domain.PetMovementType;
import com.petsistemi.domain.RuntimeRepresentationType;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Identifier case conversion must never depend on the server's locale.
 *
 * <p>In Turkish, {@code "i".toUpperCase()} is {@code "İ"} and {@code "I".toLowerCase()} is
 * {@code "ı"} — so {@code "item_display".toUpperCase()} yields {@code "İTEM_DİSPLAY"} and
 * every {@code Enum.valueOf} on it throws. This plugin targets Turkish servers, where the
 * default locale really is {@code tr_TR}, so a bare {@code toUpperCase()} on a config value
 * is a live bug rather than a theoretical one.</p>
 */
class LocaleSafetyTest {

    private Locale original;

    @BeforeEach
    void useTurkishLocale() {
        original = Locale.getDefault();
        Locale.setDefault(new Locale("tr", "TR"));
    }

    @AfterEach
    void restoreLocale() {
        Locale.setDefault(original);
    }

    private static YamlConfiguration yaml(String content) {
        YamlConfiguration cfg = new YamlConfiguration();
        try {
            cfg.loadFromString(content);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return cfg;
    }

    /** The exact combination that breaks: a lowercase enum value containing an "i". */
    @Test
    void lowercaseRepresentationAndMovementTypesParseUnderTurkishLocale() {
        PetDefinitionYamlParser.Parsed parsed = PetDefinitionYamlParser.parse("gadget", yaml("""
                display-name: "Gadget"
                representation:
                  type: item_display
                  item-material: DIAMOND
                movement:
                  type: flying_follow
                """));

        assertTrue(parsed.errors().isEmpty(), () -> "errors: " + parsed.errors());
        assertEquals(RuntimeRepresentationType.ITEM_DISPLAY, parsed.definition().representation().type());
        assertEquals(PetMovementType.FLYING_FOLLOW, parsed.definition().movement().type());
    }

    @Test
    void lowercaseEntityTypeValidatesUnderTurkishLocale() {
        PetDefinition def = PetDefinitionYamlParser.parse("kitty", yaml("""
                display-name: "Kitty"
                entity-type: villager
                """)).definition();

        List<String> errors = PetDefinitionValidator.validate(def, 1);

        assertTrue(errors.isEmpty(), () -> "errors: " + errors);
    }

    @Test
    void lowercaseParticleNameValidatesUnderTurkishLocale() {
        PetDefinitionYamlParser.Parsed parsed = PetDefinitionYamlParser.parse("spark", yaml("""
                display-name: "Spark"
                representation:
                  type: PARTICLE
                  particle-type: villager_happy
                  particle-count: 4
                """));

        List<String> errors = PetDefinitionValidator.validate(parsed.definition(), 1);

        assertTrue(errors.isEmpty(), () -> "errors: " + errors);
    }

    /**
     * Source-level guard. A single bare {@code toLowerCase()} on an identifier reintroduces
     * the whole class of bug, so none are allowed in production code.
     */
    @Test
    void noProductionCodeUsesLocaleSensitiveCaseConversion() throws Exception {
        Path sourceRoot = Path.of("src", "main", "java");
        List<String> offenders;
        try (Stream<Path> files = Files.walk(sourceRoot)) {
            offenders = files
                    .filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> {
                        try {
                            String body = Files.readString(p);
                            return body.contains(".toLowerCase()") || body.contains(".toUpperCase()");
                        } catch (Exception e) {
                            return false;
                        }
                    })
                    .map(p -> sourceRoot.relativize(p).toString())
                    .toList();
        }

        assertTrue(offenders.isEmpty(),
                () -> "Yerel ayara duyarlı harf dönüşümü (Locale.ROOT kullanın): " + offenders);
    }
}

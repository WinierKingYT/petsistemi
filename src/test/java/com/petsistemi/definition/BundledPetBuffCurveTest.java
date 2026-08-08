package com.petsistemi.definition;

import be.seeseemelk.mockbukkit.MockBukkit;
import com.petsistemi.domain.PetBuffDefinition;
import com.petsistemi.domain.PetDefinition;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.potion.PotionEffectType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Wolf, cat and allay buffs used to be a {@code switch} on the pet id in Java, scaled by
 * {@code amplifier = clamp(0..3, (level - 1) / 5)}. They now live in the pets' own
 * {@code buffs:} blocks as level tiers. This test pins the migrated curve to the original
 * formula so the move stays faithful — and stays faithful after somebody edits the YAML.
 */
class BundledPetBuffCurveTest {

    @BeforeAll
    static void bootServer() {
        MockBukkit.mock();
    }

    @AfterAll
    static void stopServer() {
        MockBukkit.unmock();
    }

    /** The formula the deleted {@code PetAbilityTask.buffAmplifier} used. */
    private static int legacyAmplifier(int level) {
        return Math.max(0, Math.min(3, (level - 1) / 5));
    }

    private static PetDefinition load(String id) throws Exception {
        try (InputStream in = BundledPetBuffCurveTest.class.getResourceAsStream("/pets/" + id + ".yml")) {
            assertNotNull(in, id + ".yml okunamadı");
            YamlConfiguration cfg = new YamlConfiguration();
            cfg.loadFromString(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            return PetDefinitionYamlParser.parse(id, cfg).definition();
        }
    }

    /** Mirrors {@code PetBuffController}: the strongest tier the pet has earned. */
    private static int effectiveAmplifier(List<PetBuffDefinition> buffs, PotionEffectType type, int level) {
        return buffs.stream()
                .filter(buff -> buff.effectType().equals(type))
                .filter(buff -> level >= buff.minLevel())
                .mapToInt(PetBuffDefinition::amplifier)
                .max()
                .orElse(-1);
    }

    private void assertCurveMatchesLegacy(String petId, PotionEffectType effect) throws Exception {
        List<PetBuffDefinition> buffs = load(petId).buffs();
        assertNotNull(buffs, petId + " buff bildirmeli");

        for (int level = 1; level <= 100; level++) {
            assertEquals(legacyAmplifier(level), effectiveAmplifier(buffs, effect, level),
                    petId + " / " + effect.getName() + " seviye " + level + " kademesi eski formülden farklı");
        }
    }

    @Test
    void wolfSpeedMatchesTheLegacyCurve() throws Exception {
        assertCurveMatchesLegacy("wolf", PotionEffectType.SPEED);
    }

    @Test
    void catSpeedMatchesTheLegacyCurve() throws Exception {
        assertCurveMatchesLegacy("cat", PotionEffectType.SPEED);
    }

    @Test
    void allayRegenerationMatchesTheLegacyCurve() throws Exception {
        assertCurveMatchesLegacy("allay", PotionEffectType.REGENERATION);
    }

    /**
     * The Java constant is {@code FAST_DIGGING}, but the registry that resolves YAML names
     * only answers to {@code HASTE} — so the obvious spelling in a pet file resolves to
     * nothing. It is now a loud parse error instead of a buff that silently never applies.
     */
    @Test
    void allayHasteMatchesTheLegacyCurve() throws Exception {
        assertCurveMatchesLegacy("allay", PotionEffectType.FAST_DIGGING);
    }

    /** Night vision was a flat, unscaled effect in the old table and must stay flat. */
    @Test
    void catNightVisionIsFlatFromLevelOne() throws Exception {
        List<PetBuffDefinition> buffs = load("cat").buffs();

        for (int level = 1; level <= 100; level++) {
            assertEquals(0, effectiveAmplifier(buffs, PotionEffectType.NIGHT_VISION, level),
                    "gece görüşü seviyeyle güçlenmemeli");
        }
    }
}

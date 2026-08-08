package com.petsistemi.definition;

import be.seeseemelk.mockbukkit.MockBukkit;
import com.petsistemi.domain.PetBuffDefinition;
import com.petsistemi.domain.PetDefinition;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.potion.PotionEffectType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The {@code buffs:} block is the only way an admin can grant potion effects without
 * touching Java. A key the parser does not recognise used to be skipped in silence, so a
 * pet could ship with a full buff list that never applied and never logged anything —
 * indistinguishable from "buffs are broken" when reported by a server owner.
 */
class PetBuffParsingTest {

    /**
     * Resolving a potion effect name goes through {@link org.bukkit.Registry}, which reads
     * {@code Bukkit.server} — so parsing a buff list is impossible without a server. That is
     * why this code path had never been under test.
     */
    @BeforeAll
    static void bootServer() {
        MockBukkit.mock();
    }

    @AfterAll
    static void stopServer() {
        MockBukkit.unmock();
    }

    private static YamlConfiguration yaml(String body) throws Exception {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.loadFromString(body);
        return cfg;
    }

    private static List<PetBuffDefinition> parse(String buffsBlock, List<String> errors) throws Exception {
        YamlConfiguration cfg = yaml("display-name: Test\nentity: WOLF\n" + buffsBlock);
        PetDefinitionYamlParser.Parsed parsed = PetDefinitionYamlParser.parse("test", cfg);
        errors.addAll(parsed.errors());
        PetDefinition definition = parsed.definition();
        return definition == null ? null : definition.buffs();
    }

    /** The spelling every bundled pet already uses. It was silently ignored. */
    @Test
    void typeAndDurationSecondsAreAccepted() throws Exception {
        List<String> errors = new ArrayList<>();

        List<PetBuffDefinition> buffs = parse("""
                buffs:
                  - type: FIRE_RESISTANCE
                    amplifier: 0
                    duration-seconds: 10
                """, errors);

        assertTrue(errors.isEmpty(), () -> "beklenmeyen hata: " + errors);
        assertNotNull(buffs, "type: yazımı tanınmalı");
        assertEquals(1, buffs.size());
        assertEquals(PotionEffectType.FIRE_RESISTANCE, buffs.get(0).effectType());
        assertEquals(200, buffs.get(0).durationTicks(), "10 saniye 200 tick olmalı");
    }

    /** The spelling the parser was written for must keep working. */
    @Test
    void effectAndDurationTicksStillWork() throws Exception {
        List<String> errors = new ArrayList<>();

        List<PetBuffDefinition> buffs = parse("""
                buffs:
                  - effect: SPEED
                    amplifier: 1
                    min-level: 6
                    duration-ticks: 70
                """, errors);

        assertTrue(errors.isEmpty(), () -> "beklenmeyen hata: " + errors);
        assertNotNull(buffs);
        assertEquals(PotionEffectType.SPEED, buffs.get(0).effectType());
        assertEquals(1, buffs.get(0).amplifier());
        assertEquals(6, buffs.get(0).minLevel());
        assertEquals(70, buffs.get(0).durationTicks());
    }

    /** A buff naming no effect at all is an authoring mistake and must be reported. */
    @Test
    void anEntryWithoutAnEffectNameIsReported() throws Exception {
        List<String> errors = new ArrayList<>();

        parse("""
                buffs:
                  - amplifier: 2
                    duration-seconds: 5
                """, errors);

        assertFalse(errors.isEmpty(), "etki adı olmayan buff sessizce atlanmamalı");
    }

    @Test
    void anUnknownEffectNameIsReported() throws Exception {
        List<String> errors = new ArrayList<>();

        parse("""
                buffs:
                  - type: SUPER_SPEED
                    amplifier: 0
                """, errors);

        assertFalse(errors.isEmpty(), "tanınmayan etki adı bildirilmeli");
    }

    /** Bundled pets are the regression guard: whatever they declare must actually load. */
    @Test
    void phoenixShipsWithWorkingBuffs() throws Exception {
        YamlConfiguration cfg;
        try (var in = getClass().getResourceAsStream("/pets/phoenix.yml")) {
            assertNotNull(in, "phoenix.yml okunamadı");
            cfg = new YamlConfiguration();
            cfg.loadFromString(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
        }

        PetDefinition phoenix = PetDefinitionYamlParser.parse("phoenix", cfg).definition();

        assertNotNull(phoenix.buffs(), "phoenix.yml buffs: bildiriyor ama yüklenmiyor");
        assertEquals(2, phoenix.buffs().size());
    }
}

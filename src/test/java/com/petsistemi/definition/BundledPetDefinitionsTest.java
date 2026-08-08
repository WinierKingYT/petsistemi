package com.petsistemi.definition;

import be.seeseemelk.mockbukkit.MockBukkit;
import com.petsistemi.domain.PetDefinition;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every bundled {@code pets/*.yml} must survive the exact pipeline the plugin runs at
 * startup: parse then validate. {@code AtomicPetDefinitionRegistry} aborts the whole
 * snapshot when any single file reports an error, so one bad shipped file takes down
 * every pet on the server.
 */
class BundledPetDefinitionsTest {

    /**
     * A pet declaring {@code buffs:} makes the parser resolve potion effect names through
     * {@link org.bukkit.Registry}, which needs {@code Bukkit.server}. That static initialiser
     * runs once per JVM and stays broken if it first runs serverless — poisoning every later
     * test in the run, not just this one.
     */
    @BeforeAll
    static void bootServer() {
        MockBukkit.mock();
    }

    @AfterAll
    static void stopServer() {
        MockBukkit.unmock();
    }

    /** Discovers the shipped pet ids from the resource directory so new pets are covered automatically. */
    static List<String> bundledPetIds() throws URISyntaxException, IOException {
        URL dir = BundledPetDefinitionsTest.class.getResource("/pets");
        assertNotNull(dir, "/pets kaynak klasörü bulunamadı");
        try (Stream<Path> files = Files.list(Path.of(dir.toURI()))) {
            return files.map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(".yml") || name.endsWith(".yaml"))
                    .map(name -> name.substring(0, name.lastIndexOf('.')))
                    .sorted()
                    .toList();
        }
    }

    private static YamlConfiguration load(String id) throws Exception {
        try (InputStream in = BundledPetDefinitionsTest.class.getResourceAsStream("/pets/" + id + ".yml")) {
            assertNotNull(in, id + ".yml okunamadı");
            YamlConfiguration cfg = new YamlConfiguration();
            cfg.loadFromString(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            return cfg;
        }
    }

    @Test
    void everyBundledPetParsesAndValidates() throws Exception {
        List<String> ids = bundledPetIds();
        assertFalse(ids.isEmpty(), "en az bir paketli pet tanımı olmalı");

        List<String> failures = new ArrayList<>();
        for (String id : ids) {
            YamlConfiguration cfg = load(id);
            PetDefinitionYamlParser.Parsed parsed = PetDefinitionYamlParser.parse(id, cfg);

            List<String> errors = new ArrayList<>(parsed.errors());
            PetDefinition definition = parsed.definition();
            if (definition == null) {
                errors.add("tanım üretilemedi");
            } else {
                errors.addAll(PetDefinitionValidator.validate(definition, cfg.getInt("schema-version", 1)));
            }
            if (!errors.isEmpty()) {
                failures.add(id + ".yml -> " + String.join("; ", errors));
            }
        }

        assertTrue(failures.isEmpty(),
                () -> "Paketli pet tanımları sunucu açılışında reddedilirdi:\n  " + String.join("\n  ", failures));
    }

    @Test
    void freshInstallCopiesEveryBundledPet() throws Exception {
        List<String> bundled = bundledPetIds().stream().map(id -> id + ".yml").sorted().toList();
        List<String> copied = AtomicPetDefinitionRegistry.DEFAULT_PET_FILES.stream().sorted().toList();

        assertEquals(bundled, copied,
                "AtomicPetDefinitionRegistry.DEFAULT_PET_FILES, resources/pets/ içeriğiyle aynı olmalı; "
                        + "aksi halde paketlenmiş petler temiz kurulumda sunucuya kopyalanmaz.");
    }

    /**
     * A pet's {@code permission:} node is enforced on summon. Bukkit denies unregistered nodes to
     * non-ops, so a node missing from plugin.yml silently locks that pet away from normal players.
     */
    @Test
    void everyBundledPetPermissionIsDeclaredInPluginYml() throws Exception {
        YamlConfiguration pluginYml;
        try (InputStream in = getClass().getResourceAsStream("/plugin.yml")) {
            assertNotNull(in, "plugin.yml okunamadı");
            pluginYml = new YamlConfiguration();
            pluginYml.loadFromString(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }

        List<String> undeclared = new ArrayList<>();
        for (String id : bundledPetIds()) {
            String permission = PetDefinitionYamlParser.parse(id, load(id)).definition().permission();
            if (permission != null && !pluginYml.isConfigurationSection("permissions." + permission)) {
                undeclared.add(id + ".yml -> " + permission);
            }
        }

        assertTrue(undeclared.isEmpty(), () -> "plugin.yml'de tanımsız pet izinleri: " + undeclared);
    }

    @Test
    void everyBundledPetDeclaresSchemaVersionOne() throws Exception {
        List<String> missing = new ArrayList<>();
        for (String id : bundledPetIds()) {
            YamlConfiguration cfg = load(id);
            if (!cfg.isSet("schema-version")) {
                missing.add(id + ".yml");
            } else if (cfg.getInt("schema-version") != 1) {
                missing.add(id + ".yml (schema-version=" + cfg.getInt("schema-version") + ")");
            }
        }
        assertTrue(missing.isEmpty(), () -> "schema-version: 1 eksik: " + missing);
    }

    /** The startup summary is the first thing consulted when a pet misbehaves in game. */
    @Test
    void startupSummaryNamesEachPetWithItsRepresentationAndMovement() throws Exception {
        java.util.Map<String, PetDefinition> loaded = new java.util.LinkedHashMap<>();
        for (String id : bundledPetIds()) {
            PetDefinition def = PetDefinitionYamlParser.parse(id, load(id)).definition();
            if (def != null) {
                loaded.put(id, def);
            }
        }

        String summary = AtomicPetDefinitionRegistry.summarise(loaded);

        assertTrue(summary.contains("spirit_flame(PARTICLE/HOVER)"),
                () -> "spirit_flame PARTICLE+HOVER olarak görünmeli: " + summary);
        assertTrue(summary.contains("wolf(ENTITY/"), () -> "wolf ENTITY olmalı: " + summary);
        assertTrue(summary.contains("arcane_crystal(ITEM_DISPLAY/ORBIT)"), () -> summary);
    }
}

package com.petsistemi.definition;

import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Startup and reload deliberately disagree about broken definition files:
 * boot keeps the server usable, reload refuses to swap in a half-valid snapshot.
 */
class PetDefinitionLoadFailureModeTest {

    private File dataFolder;
    private File petsDir;
    private JavaPlugin plugin;

    @BeforeEach
    void setUp() {
        dataFolder = new File("build/tmp-failmode-" + UUID.randomUUID());
        petsDir = new File(dataFolder, "pets");
        petsDir.mkdirs();
        plugin = mock(JavaPlugin.class);
        when(plugin.getDataFolder()).thenReturn(dataFolder);
    }

    private void writePet(String fileName, String body) throws Exception {
        Files.writeString(new File(petsDir, fileName).toPath(), body);
    }

    private static final String VALID_WOLF = """
            schema-version: 1
            display-name: "Kurt"
            entity-type: WOLF
            """;

    private static final String VALID_CAT = """
            schema-version: 1
            display-name: "Kedi"
            entity-type: CAT
            """;

    /** maximum-level: -5 fails validation, so this file can never produce a definition. */
    private static final String BROKEN = """
            schema-version: 1
            display-name: "Bozuk"
            entity-type: WOLF
            progression:
              maximum-level: -5
            """;

    @Test
    void startupLoadsValidPetsAndSkipsBrokenOnes() throws Exception {
        writePet("wolf.yml", VALID_WOLF);
        writePet("cat.yml", VALID_CAT);
        writePet("broken.yml", BROKEN);

        AtomicPetDefinitionRegistry registry = new AtomicPetDefinitionRegistry(plugin);
        registry.reload();

        assertEquals(2, registry.getAll().size(), "geçerli petler bozuk dosyaya rağmen yüklenmeli");
        assertTrue(registry.find("wolf").isPresent());
        assertTrue(registry.find("cat").isPresent());
        assertTrue(registry.find("broken").isEmpty(), "bozuk tanım yayımlanmamalı");
    }

    @Test
    void scanReportsBrokenFileWithItsErrors() throws Exception {
        writePet("wolf.yml", VALID_WOLF);
        writePet("broken.yml", BROKEN);

        AtomicPetDefinitionRegistry.ScanResult scan = new AtomicPetDefinitionRegistry(plugin).scanPetsFolder();

        assertEquals(1, scan.definitions().size());
        assertTrue(scan.hasErrors());
        assertTrue(scan.errorsPerFile().containsKey("broken.yml"));
        assertTrue(scan.errorSummary().contains("broken.yml"),
                () -> "özet hatalı dosyayı adlandırmalı: " + scan.errorSummary());
    }

    @Test
    void reloadCandidateStaysStrictSoRunningSnapshotSurvives() throws Exception {
        writePet("wolf.yml", VALID_WOLF);
        writePet("cat.yml", VALID_CAT);

        AtomicPetDefinitionRegistry registry = new AtomicPetDefinitionRegistry(plugin);
        registry.reload();
        assertEquals(2, registry.getAll().size());

        // Admin now edits in a broken file and runs /petadmin reload.
        writePet("broken.yml", BROKEN);

        assertThrows(IllegalStateException.class, registry::loadCandidateSnapshot,
                "reload adayı tek hatalı dosyada bile reddedilmeli");
        assertEquals(2, registry.getAll().size(),
                "aday reddedildiğinde çalışan snapshot dokunulmadan kalmalı");
        assertTrue(registry.find("wolf").isPresent());
    }

    @Test
    void startupWithOnlyBrokenFilesPublishesEmptyRegistryWithoutThrowing() throws Exception {
        writePet("broken.yml", BROKEN);

        AtomicPetDefinitionRegistry registry = new AtomicPetDefinitionRegistry(plugin);
        registry.reload();

        assertTrue(registry.getAll().isEmpty());
        assertFalse(registry.find("broken").isPresent());
    }
}

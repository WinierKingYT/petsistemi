package com.petsistemi.pack;

import be.seeseemelk.mockbukkit.MockBukkit;
import com.petsistemi.definition.AtomicPetDefinitionRegistry;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PetPackServiceTest {
    @TempDir Path temporary;
    private JavaPlugin plugin;
    private AtomicPetDefinitionRegistry registry;

    @BeforeEach void setUp() {
        MockBukkit.mock();
        plugin = mock(JavaPlugin.class);
        when(plugin.getDataFolder()).thenReturn(temporary.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        registry = new AtomicPetDefinitionRegistry(plugin);
        registry.reload();
    }

    @AfterEach void tearDown() { MockBukkit.unmock(); }

    @Test void installsNamespacedDefinitionsAndTracksReceipt() throws Exception {
        Path archive = archive("forest-pack", "forest", List.of(), "fox", minimalPet());
        DefaultPetPackService service = service();

        PetPackInstallResult result = service.install(archive, archive.toUri());

        assertTrue(result.success(), result.message());
        assertTrue(registry.find("forest:fox").isPresent());
        assertEquals(List.of("forest:fox"), result.installedDefinitionIds());
        assertEquals("forest-pack", service.installed().iterator().next().id());
    }

    @Test void missingDependencyRejectsWithoutPublishingFiles() throws Exception {
        Path archive = archive("forest-pack", "forest", List.of("base-pack"), "fox", minimalPet());

        PetPackInstallResult result = service().install(archive, archive.toUri());

        assertFalse(result.success());
        assertTrue(result.message().contains("bağımlılık"));
        assertTrue(registry.find("forest:fox").isEmpty());
    }

    @Test void zipSlipEntryIsRejected() throws Exception {
        Path archive = temporary.resolve("evil.petpack");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive))) {
            put(zip, "pack.yml", manifest("evil-pack", "evil", List.of()));
            put(zip, "../outside.yml", "bad");
            put(zip, "pets/fox.yml", minimalPet());
        }

        PetPackInstallResult result = service().install(archive, archive.toUri());

        assertFalse(result.success());
        assertFalse(Files.exists(temporary.getParent().resolve("outside.yml")));
    }

    @Test void upgradeRemovesDefinitionsNoLongerPresent() throws Exception {
        DefaultPetPackService service = service();
        Path first = archiveWithPets("forest-pack", "forest", List.of(),
                java.util.Map.of("fox", minimalPet(), "owl", minimalPet()));
        assertTrue(service.install(first, first.toUri()).success());
        Path second = archive("forest-pack", "forest", List.of(), "fox", minimalPet());

        PetPackInstallResult result = service.install(second, second.toUri());

        assertTrue(result.success(), result.message());
        assertTrue(registry.find("forest:fox").isPresent());
        assertTrue(registry.find("forest:owl").isEmpty());
    }

    private DefaultPetPackService service() { return new DefaultPetPackService(plugin, registry, 16, 1_000_000, 2_000_000); }
    private Path archive(String id, String namespace, List<String> dependencies, String petId, String petYaml) throws Exception {
        return archiveWithPets(id, namespace, dependencies, java.util.Map.of(petId, petYaml));
    }
    private Path archiveWithPets(String id, String namespace, List<String> dependencies,
                                 java.util.Map<String, String> pets) throws Exception {
        Path archive = temporary.resolve(id + ".petpack");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive))) {
            put(zip, "pack.yml", manifest(id, namespace, dependencies));
            for (var pet : pets.entrySet()) put(zip, "pets/" + pet.getKey() + ".yml", pet.getValue());
        }
        return archive;
    }
    private static String manifest(String id, String namespace, List<String> dependencies) {
        return "schema-version: 1\nid: " + id + "\nnamespace: " + namespace + "\nversion: 1.0.0\nminimum-engine-version: 0.2.0\ndependencies: " + dependencies + "\n";
    }
    private static String minimalPet() { return "schema-version: 1\ndisplay-name: Forest Fox\ngui-material: BONE\nentity-type: WOLF\n"; }
    private static void put(ZipOutputStream zip, String name, String value) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(value.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}

package com.petsistemi.config;

import com.petsistemi.bootstrap.PetPluginContext;

import com.petsistemi.bootstrap.TaskRegistry;
import com.petsistemi.bootstrap.registrar.SchedulerRegistrar;
import com.petsistemi.definition.AtomicPetDefinitionRegistry;
import com.petsistemi.message.MessageBundle;
import com.petsistemi.message.MessageService;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.File;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PetAtomicReloadTest {

    @Test
    void testValidCandidateConfigLoadsCleanly() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("locale", "tr_TR");
        yaml.set("limits.maximum-owned-pets", 10);
        yaml.set("naming.minimum-length", 3);
        yaml.set("naming.maximum-length", 12);
        yaml.set("progression.enabled", true);
        yaml.set("progression.maximum-level", 50);
        yaml.set("progression.xp-per-level", 200L);
        yaml.set("runtime.tick-interval-ticks", 5L);
        yaml.set("runtime.start-distance", 5.0);
        yaml.set("runtime.stop-distance", 2.0);
        yaml.set("runtime.teleport-distance", 15.0);
        yaml.set("runtime.follow-speed", 1.2);

        PluginConfiguration config = PluginConfigurationLoader.load(yaml);
        assertNotNull(config);
        assertEquals("tr_TR", config.locale());
        assertEquals(10, config.limits().maximumOwnedPets());
        assertEquals(200L, config.progression().xpPerLevel());
    }

    @Test
    void testInvalidCandidateConfigThrowsAndDoesNotPublish() {
        YamlConfiguration invalidYaml = new YamlConfiguration();
        invalidYaml.set("progression.xp-per-level", -10L); // Invalid!

        AtomicReference<RuntimeConfigurationSnapshot> snapshotRef = new AtomicReference<>(null);

        assertThrows(IllegalStateException.class, () -> {
            PluginConfiguration candidateConfig = PluginConfigurationLoader.load(invalidYaml);
            RuntimeConfigurationSnapshot candidateSnapshot = new RuntimeConfigurationSnapshot(candidateConfig, null, null, System.currentTimeMillis());
            snapshotRef.set(candidateSnapshot);
        });

        assertNull(snapshotRef.get(), "Snapshot MUST remain null if candidate validation failed!");
    }

    @Test
    void testLocaleChangePublishesNewBundle() {
        JavaPlugin mockPlugin = mock(JavaPlugin.class);
        YamlConfiguration configYaml = new YamlConfiguration();
        configYaml.set("locale", "tr_TR");
        when(mockPlugin.getConfig()).thenReturn(configYaml);
        when(mockPlugin.getDataFolder()).thenReturn(new File("target/test-data-" + System.currentTimeMillis()));

        MessageService messageService = new MessageService(mockPlugin);
        MessageBundle initialBundle = messageService.currentBundle();
        assertNotNull(initialBundle);

        MessageBundle enUsCandidate = messageService.loadCandidate("en_US");
        assertNotNull(enUsCandidate);
        messageService.publish(enUsCandidate);

        assertEquals(enUsCandidate, messageService.currentBundle(), "Publishing en_US bundle MUST update current bundle!");
    }

    @Test
    void testInvalidPetDefinitionDoesNotReturnReloadSuccess() throws Exception {
        JavaPlugin mockPlugin = mock(JavaPlugin.class);
        File tempFolder = new File("build/tmp-test-" + System.currentTimeMillis());
        File petsDir = new File(tempFolder, "pets");
        petsDir.mkdirs();

        File brokenFile = new File(petsDir, "broken_pet.yml");
        java.nio.file.Files.writeString(brokenFile.toPath(), "display-name: Broken\nprogression:\n  maximum-level: -5\n");

        when(mockPlugin.getDataFolder()).thenReturn(tempFolder);

        AtomicPetDefinitionRegistry registry = new AtomicPetDefinitionRegistry(mockPlugin);
        assertThrows(IllegalStateException.class, registry::loadCandidateSnapshot, "Invalid definition file MUST throw IllegalStateException!");
    }
}

package com.petsistemi.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class PetAtomicReloadTest {

    @Test
    void testValidConfigurationLoadsCleanly() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("locale", "tr_TR");
        yaml.set("limits.maximum-owned-pets", 10);
        yaml.set("naming.minimum-length", 3);
        yaml.set("naming.maximum-length", 12);
        yaml.set("runtime.tick-interval-ticks", 5);
        yaml.set("runtime.start-distance", 5.0);
        yaml.set("runtime.stop-distance", 2.0);
        yaml.set("runtime.teleport-distance", 15.0);
        yaml.set("runtime.follow-speed", 1.2);

        PluginConfiguration config = PluginConfigurationLoader.load(yaml);
        assertNotNull(config);
        assertEquals("tr_TR", config.locale());
        assertEquals(10, config.limits().maximumOwnedPets());
    }

    @Test
    void testInvalidConfigurationThrowsAndDoesNotPublish() {
        YamlConfiguration invalidYaml = new YamlConfiguration();
        invalidYaml.set("limits.maximum-owned-pets", -5); // Invalid!

        AtomicReference<RuntimeConfigurationSnapshot> snapshotRef = new AtomicReference<>(null);

        assertThrows(IllegalStateException.class, () -> {
            PluginConfiguration candidateConfig = PluginConfigurationLoader.load(invalidYaml);
            RuntimeConfigurationSnapshot candidateSnapshot = new RuntimeConfigurationSnapshot(candidateConfig, null, null, System.currentTimeMillis());
            snapshotRef.set(candidateSnapshot);
        });

        assertNull(snapshotRef.get(), "Snapshot MUST remain null if candidate validation failed!");
    }
}

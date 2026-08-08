package com.petsistemi.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RidingFeatureConfigTest {

    private PluginConfiguration load(String yamlText) {
        YamlConfiguration yaml = new YamlConfiguration();
        if (yamlText != null) {
            try {
                yaml.loadFromString(yamlText);
            } catch (Exception ignored) {
            }
        }
        return PluginConfigurationLoader.load(yaml);
    }

    @Test
    void ridingDefaultsToDisabled() {
        assertFalse(load(null).features().ridingEnabled());
    }

    @Test
    void ridingEnablesViaConfig() {
        PluginConfiguration config = load("features:\n  riding:\n    enabled: true\n");
        assertTrue(config.features().ridingEnabled());
    }

    @Test
    void otherFeatureFlagsUnaffected() {
        PluginConfiguration config = load("features:\n  riding:\n    enabled: true\n");
        assertTrue(config.features().buffsEnabled(), "buff'lar varsayılan olarak açık");
        assertFalse(config.features().particlesEnabled());
        assertFalse(config.features().magnetEnabled());
        assertTrue(config.features().ridingEnabled());
    }

    @Test
    void threeArgConvenienceConstructorDisablesRiding() {
        PluginConfiguration.FeaturesConfiguration features = new PluginConfiguration.FeaturesConfiguration(true, true, true);
        assertTrue(features.buffsEnabled());
        assertFalse(features.ridingEnabled());
    }
}

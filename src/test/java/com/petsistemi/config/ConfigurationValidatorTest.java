package com.petsistemi.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ConfigurationValidatorTest {

    @Test
    void testValidConfigurationPassesValidation() {
        PluginConfiguration config = new PluginConfiguration(
                new PluginConfiguration.LimitsConfiguration(20),
                new PluginConfiguration.NamingConfiguration(2, 16, false, false),
                new PluginConfiguration.ProgressionConfiguration(true, 100),
                new PluginConfiguration.RuntimeConfiguration(5L, 5.0, 2.0, 15.0, 1.2),
                new PluginConfiguration.DatabaseConfiguration(true, true, 5),
                new PluginConfiguration.GuiConfiguration("Pet Menüsü", 6),
                new PluginConfiguration.DiagnosticsConfiguration(100L),
                new PluginConfiguration.DefinitionConfiguration("KEEP_OLD_ON_ANY_ERROR"),
                "tr_TR"
        );

        List<String> errors = ConfigurationValidator.validate(config);
        assertTrue(errors.isEmpty(), "Valid configuration must produce 0 errors");
    }

    @Test
    void testInvalidConfigurationFailsFast() {
        PluginConfiguration invalidConfig = new PluginConfiguration(
                new PluginConfiguration.LimitsConfiguration(-5), // Invalid limit
                new PluginConfiguration.NamingConfiguration(20, 5, false, false), // Min > Max
                new PluginConfiguration.ProgressionConfiguration(true, 100),
                new PluginConfiguration.RuntimeConfiguration(-1L, 2.0, 5.0, 10.0, -1.0), // Invalid ticks & stop >= start
                new PluginConfiguration.DatabaseConfiguration(true, true, 0), // Invalid backup count
                new PluginConfiguration.GuiConfiguration("Menu", 6),
                new PluginConfiguration.DiagnosticsConfiguration(100L),
                new PluginConfiguration.DefinitionConfiguration("KEEP_OLD_ON_ANY_ERROR"),
                "" // Empty locale
        );

        List<String> errors = ConfigurationValidator.validate(invalidConfig);
        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(e -> e.contains("maximum-owned-pets")));
        assertTrue(errors.stream().anyMatch(e -> e.contains("minimum-length")));
        assertTrue(errors.stream().anyMatch(e -> e.contains("stop-distance")));
        assertTrue(errors.stream().anyMatch(e -> e.contains("locale")));
    }

    @Test
    void networkSyncRequiresMysqlBackend() {
        PluginConfiguration base = new PluginConfiguration(
                new PluginConfiguration.LimitsConfiguration(20),
                new PluginConfiguration.NamingConfiguration(2, 16, false, false),
                new PluginConfiguration.ProgressionConfiguration(true, 100),
                new PluginConfiguration.RuntimeConfiguration(5L, 5.0, 2.0, 15.0, 1.2),
                new PluginConfiguration.DatabaseConfiguration(true, true, 5),
                new PluginConfiguration.GuiConfiguration("Menu", 6), new PluginConfiguration.DiagnosticsConfiguration(100),
                new PluginConfiguration.DefinitionConfiguration("KEEP_OLD_ON_ANY_ERROR"),
                new PluginConfiguration.FeaturesConfiguration(false, false, false, false),
                new PluginConfiguration.EcosystemConfiguration(
                        new PluginConfiguration.NetworkConfiguration(true, "server-a", 20, 100, 10000),
                        PluginConfiguration.PetPackConfiguration.defaults(), PluginConfiguration.MarketplaceConfiguration.defaults()), "tr_TR");
        assertTrue(ConfigurationValidator.validate(base).stream().anyMatch(error -> error.contains("MYSQL")));
    }
}

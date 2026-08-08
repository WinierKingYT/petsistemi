package com.petsistemi.config;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;

public final class PluginConfigurationLoader {

    public static PluginConfiguration load(FileConfiguration config) {
        String locale = config.getString("locale", "tr_TR");

        int maxPets = config.getInt("limits.maximum-owned-pets", 20);

        int minLen = config.getInt("naming.minimum-length", 2);
        int maxLen = config.getInt("naming.maximum-length", 16);
        boolean allowColors = config.getBoolean("naming.allow-colors", false);
        boolean allowFormatting = config.getBoolean("naming.allow-formatting", false);

        boolean progressionEnabled = config.getBoolean("progression.enabled", true);
        int maxLevel = config.getInt("progression.maximum-level", 100);
        long xpPerLevel = config.getLong("progression.xp-per-level", 100L);
        long passiveXpPerMinute = config.getLong("progression.passive-xp-per-minute", 10L);
        double walkXpThreshold = config.getDouble("progression.walk-xp-threshold", 50.0);
        long walkXpAmount = config.getLong("progression.walk-xp-amount", 5L);
        long blockBreakXp = config.getLong("progression.block-break-xp", 2L);
        double killXpMultiplier = config.getDouble("progression.kill-xp-multiplier", 0.5);

        long tickInterval = config.getLong("runtime.tick-interval-ticks", 5L);
        double startDist = config.getDouble("runtime.start-distance", 5.0);
        double stopDist = config.getDouble("runtime.stop-distance", 2.0);
        double teleportDist = config.getDouble("runtime.teleport-distance", 15.0);
        double followSpeed = config.getDouble("runtime.follow-speed", 1.2);

        boolean backupEnabled = config.getBoolean("database.migration-backup.enabled", true);
        boolean failOnBackupError = config.getBoolean("database.migration-backup.fail-startup-on-backup-error", true);
        int maxBackups = config.getInt("database.migration-backup.maximum-backups", 5);
        String databaseBackend = config.getString("database.backend", "SQLITE").trim().toUpperCase(java.util.Locale.ROOT);
        PluginConfiguration.MysqlConfiguration mysql = new PluginConfiguration.MysqlConfiguration(
                config.getString("database.mysql.host", "127.0.0.1"),
                config.getInt("database.mysql.port", 3306),
                config.getString("database.mysql.database", "petsistemi"),
                config.getString("database.mysql.username", "root"),
                config.getString("database.mysql.password", ""),
                config.getBoolean("database.mysql.use-ssl", false),
                config.getInt("database.mysql.connect-timeout-ms", 10000));

        PluginConfiguration.NetworkConfiguration network = new PluginConfiguration.NetworkConfiguration(
                config.getBoolean("ecosystem.network.enabled", false),
                config.getString("ecosystem.network.server-id", "server-1"),
                config.getLong("ecosystem.network.poll-interval-ticks", 20L),
                config.getInt("ecosystem.network.batch-size", 100),
                config.getLong("ecosystem.network.retention-hours", 24L) * 3_600_000L);
        PluginConfiguration.PetPackConfiguration petPacks = new PluginConfiguration.PetPackConfiguration(
                config.getInt("ecosystem.pet-packs.maximum-files", 128),
                config.getLong("ecosystem.pet-packs.maximum-archive-bytes", 10_485_760L),
                config.getLong("ecosystem.pet-packs.maximum-expanded-bytes", 52_428_800L));
        PluginConfiguration.MarketplaceConfiguration marketplace = new PluginConfiguration.MarketplaceConfiguration(
                config.getBoolean("ecosystem.marketplace.enabled", false),
                config.getString("ecosystem.marketplace.catalog-url", ""),
                config.getBoolean("ecosystem.marketplace.require-sha256", true),
                config.getLong("ecosystem.marketplace.maximum-download-bytes", 10_485_760L),
                config.getInt("ecosystem.marketplace.request-timeout-ms", 10000));

        // Replaces the old features.abilities.enabled switch, which gated a Java-side buff table
        // rather than the pets' own `buffs:` blocks. On by default: a pet that declares no buffs
        // still grants none, so the switch only exists to silence every pet at once.
        boolean buffsEnabled = config.getBoolean("features.buffs.enabled", true);
        boolean particlesEnabled = config.getBoolean("features.particles.enabled", false);
        boolean magnetEnabled = config.getBoolean("features.magnet.enabled", false);
        boolean ridingEnabled = config.getBoolean("features.riding.enabled", false);

        boolean idleSleepEnabled = config.getBoolean("features.idle-sleep.enabled", false);
        int idleSleepSeconds = Math.max(5, config.getInt("features.idle-sleep.idle-seconds", 45));
        boolean reactionsEnabled = config.getBoolean("features.reactions.enabled", false);
        boolean levelScalingEnabled = config.getBoolean("features.level-scaling.enabled", false);
        double levelScalingGrowth = Math.max(0.0, config.getDouble("features.level-scaling.growth-per-level", 0.02));
        double levelScalingMax = Math.max(1.0, config.getDouble("features.level-scaling.max-multiplier", 1.5));

        PluginConfiguration pluginConfig = new PluginConfiguration(
                new PluginConfiguration.LimitsConfiguration(maxPets),
                new PluginConfiguration.NamingConfiguration(minLen, maxLen, allowColors, allowFormatting),
                new PluginConfiguration.ProgressionConfiguration(
                        progressionEnabled,
                        maxLevel,
                        xpPerLevel,
                        passiveXpPerMinute,
                        walkXpThreshold,
                        walkXpAmount,
                        blockBreakXp,
                        killXpMultiplier
                ),
                new PluginConfiguration.RuntimeConfiguration(tickInterval, startDist, stopDist, teleportDist, followSpeed),
                new PluginConfiguration.DatabaseConfiguration(databaseBackend, backupEnabled, failOnBackupError, maxBackups, mysql),
                new PluginConfiguration.GuiConfiguration(config.getString("gui.title", "Pet Menüsü"), 6),
                new PluginConfiguration.DiagnosticsConfiguration(100L),
                new PluginConfiguration.DefinitionConfiguration("KEEP_OLD_ON_ANY_ERROR"),
                new PluginConfiguration.FeaturesConfiguration(
                        buffsEnabled, particlesEnabled, magnetEnabled, ridingEnabled,
                        idleSleepEnabled, idleSleepSeconds, reactionsEnabled,
                        levelScalingEnabled, levelScalingGrowth, levelScalingMax),
                new PluginConfiguration.EcosystemConfiguration(network, petPacks, marketplace),
                locale
        );

        List<String> errors = ConfigurationValidator.validate(pluginConfig);
        if (!errors.isEmpty()) {
            throw new IllegalStateException("Config doğrulama hatası! Sebepler: " + String.join(", ", errors));
        }

        return pluginConfig;
    }
}

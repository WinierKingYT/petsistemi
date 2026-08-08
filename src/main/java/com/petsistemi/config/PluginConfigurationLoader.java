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
                new PluginConfiguration.DatabaseConfiguration(backupEnabled, failOnBackupError, maxBackups),
                new PluginConfiguration.GuiConfiguration(config.getString("gui.title", "Pet Menüsü"), 6),
                new PluginConfiguration.DiagnosticsConfiguration(100L),
                new PluginConfiguration.DefinitionConfiguration("KEEP_OLD_ON_ANY_ERROR"),
                new PluginConfiguration.FeaturesConfiguration(
                        buffsEnabled, particlesEnabled, magnetEnabled, ridingEnabled,
                        idleSleepEnabled, idleSleepSeconds, reactionsEnabled,
                        levelScalingEnabled, levelScalingGrowth, levelScalingMax),
                locale
        );

        List<String> errors = ConfigurationValidator.validate(pluginConfig);
        if (!errors.isEmpty()) {
            throw new IllegalStateException("Config doğrulama hatası! Sebepler: " + String.join(", ", errors));
        }

        return pluginConfig;
    }
}

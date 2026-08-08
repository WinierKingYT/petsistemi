package com.petsistemi.config;

public record PluginConfiguration(
        LimitsConfiguration limits,
        NamingConfiguration naming,
        ProgressionConfiguration progression,
        RuntimeConfiguration runtime,
        DatabaseConfiguration database,
        GuiConfiguration gui,
        DiagnosticsConfiguration diagnostics,
        DefinitionConfiguration definition,
        FeaturesConfiguration features,
        EcosystemConfiguration ecosystem,
        String locale
) {
    public PluginConfiguration(String locale, LimitsConfiguration limits, NamingConfiguration naming, ProgressionConfiguration progression, RuntimeConfiguration runtime, DatabaseConfiguration database) {
        this(limits, naming, progression, runtime, database, new GuiConfiguration("Pet Menüsü", 6), new DiagnosticsConfiguration(100L), new DefinitionConfiguration("KEEP_OLD_ON_ANY_ERROR"), new FeaturesConfiguration(false, false, false, false), EcosystemConfiguration.defaults(), locale);
    }

    public PluginConfiguration(LimitsConfiguration limits, NamingConfiguration naming, ProgressionConfiguration progression, RuntimeConfiguration runtime, DatabaseConfiguration database, GuiConfiguration gui, DiagnosticsConfiguration diagnostics, DefinitionConfiguration definition, String locale) {
        this(limits, naming, progression, runtime, database, gui, diagnostics, definition, new FeaturesConfiguration(false, false, false, false), EcosystemConfiguration.defaults(), locale);
    }

    /** Backward-compatible constructor used by existing integrations and tests. */
    public PluginConfiguration(LimitsConfiguration limits, NamingConfiguration naming,
                               ProgressionConfiguration progression, RuntimeConfiguration runtime,
                               DatabaseConfiguration database, GuiConfiguration gui,
                               DiagnosticsConfiguration diagnostics, DefinitionConfiguration definition,
                               FeaturesConfiguration features, String locale) {
        this(limits, naming, progression, runtime, database, gui, diagnostics, definition,
                features, EcosystemConfiguration.defaults(), locale);
    }

    public record LimitsConfiguration(int maximumOwnedPets) {}
    public record NamingConfiguration(int minimumLength, int maximumLength, boolean allowColors, boolean allowFormatting) {}
    public record ProgressionConfiguration(
            boolean enabled,
            int maximumLevel,
            long xpPerLevel,
            long passiveXpPerMinute,
            double walkXpThreshold,
            long walkXpAmount,
            long blockBreakXp,
            double killXpMultiplier
    ) {
        public ProgressionConfiguration(boolean enabled, int maximumLevel) {
            this(enabled, maximumLevel, 100L, 10L, 50.0, 5L, 2L, 0.5);
        }
    }
    public record RuntimeConfiguration(long tickIntervalTicks, double startDistance, double stopDistance, double teleportDistance, double followSpeed) {}
    public record DatabaseConfiguration(String backend, boolean backupEnabled, boolean failOnBackupError,
                                        int maxBackups, MysqlConfiguration mysql) {
        public DatabaseConfiguration(boolean backupEnabled, boolean failOnBackupError, int maxBackups) {
            this("SQLITE", backupEnabled, failOnBackupError, maxBackups, MysqlConfiguration.defaults());
        }
    }
    public record MysqlConfiguration(String host, int port, String database, String username,
                                     String password, boolean useSsl, int connectTimeoutMs) {
        public static MysqlConfiguration defaults() {
            return new MysqlConfiguration("127.0.0.1", 3306, "petsistemi", "root", "", false, 10000);
        }
    }
    public record NetworkConfiguration(boolean enabled, String serverId, long pollIntervalTicks,
                                       int batchSize, long retentionMillis) {
        public static NetworkConfiguration defaults() {
            return new NetworkConfiguration(false, "server-1", 20L, 100, 86_400_000L);
        }
    }
    public record PetPackConfiguration(int maximumFiles, long maximumArchiveBytes,
                                       long maximumExpandedBytes) {
        public static PetPackConfiguration defaults() {
            return new PetPackConfiguration(128, 10_485_760L, 52_428_800L);
        }
    }
    public record MarketplaceConfiguration(boolean enabled, String catalogUrl,
                                           boolean requireSha256, long maximumDownloadBytes,
                                           int requestTimeoutMs) {
        public static MarketplaceConfiguration defaults() {
            return new MarketplaceConfiguration(false, "", true, 10_485_760L, 10000);
        }
    }
    public record EcosystemConfiguration(NetworkConfiguration network, PetPackConfiguration petPacks,
                                         MarketplaceConfiguration marketplace) {
        public static EcosystemConfiguration defaults() {
            return new EcosystemConfiguration(NetworkConfiguration.defaults(), PetPackConfiguration.defaults(),
                    MarketplaceConfiguration.defaults());
        }
    }
    public record GuiConfiguration(String title, int rows) {}
    public record DiagnosticsConfiguration(long slowQueryThresholdMs) {}
    public record DefinitionConfiguration(String reloadPolicy) {}
    public record FeaturesConfiguration(
            boolean buffsEnabled,
            boolean particlesEnabled,
            boolean magnetEnabled,
            boolean ridingEnabled,
            boolean idleSleepEnabled,
            int idleSleepSeconds,
            boolean reactionsEnabled,
            boolean levelScalingEnabled,
            double levelScalingGrowthPerLevel,
            double levelScalingMaxMultiplier
    ) {
        public FeaturesConfiguration(boolean buffsEnabled, boolean particlesEnabled, boolean magnetEnabled, boolean ridingEnabled) {
            this(buffsEnabled, particlesEnabled, magnetEnabled, ridingEnabled,
                    false, 45, false, false, 0.02, 1.5);
        }

        public FeaturesConfiguration(boolean buffsEnabled, boolean particlesEnabled, boolean magnetEnabled) {
            this(buffsEnabled, particlesEnabled, magnetEnabled, false);
        }
    }
}

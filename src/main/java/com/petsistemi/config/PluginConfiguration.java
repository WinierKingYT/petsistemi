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
        String locale
) {
    public PluginConfiguration(String locale, LimitsConfiguration limits, NamingConfiguration naming, ProgressionConfiguration progression, RuntimeConfiguration runtime, DatabaseConfiguration database) {
        this(limits, naming, progression, runtime, database, new GuiConfiguration("Pet Menüsü", 6), new DiagnosticsConfiguration(100L), new DefinitionConfiguration("KEEP_OLD_ON_ANY_ERROR"), new FeaturesConfiguration(false, false, false, false), locale);
    }

    public PluginConfiguration(LimitsConfiguration limits, NamingConfiguration naming, ProgressionConfiguration progression, RuntimeConfiguration runtime, DatabaseConfiguration database, GuiConfiguration gui, DiagnosticsConfiguration diagnostics, DefinitionConfiguration definition, String locale) {
        this(limits, naming, progression, runtime, database, gui, diagnostics, definition, new FeaturesConfiguration(false, false, false, false), locale);
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
    public record DatabaseConfiguration(boolean backupEnabled, boolean failOnBackupError, int maxBackups) {}
    public record GuiConfiguration(String title, int rows) {}
    public record DiagnosticsConfiguration(long slowQueryThresholdMs) {}
    public record DefinitionConfiguration(String reloadPolicy) {}
    public record FeaturesConfiguration(
            boolean abilitiesEnabled,
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
        public FeaturesConfiguration(boolean abilitiesEnabled, boolean particlesEnabled, boolean magnetEnabled, boolean ridingEnabled) {
            this(abilitiesEnabled, particlesEnabled, magnetEnabled, ridingEnabled,
                    false, 45, false, false, 0.02, 1.5);
        }

        public FeaturesConfiguration(boolean abilitiesEnabled, boolean particlesEnabled, boolean magnetEnabled) {
            this(abilitiesEnabled, particlesEnabled, magnetEnabled, false);
        }
    }
}

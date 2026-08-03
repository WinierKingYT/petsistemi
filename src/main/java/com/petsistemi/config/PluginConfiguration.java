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
        String locale
) {
    public PluginConfiguration(String locale, LimitsConfiguration limits, NamingConfiguration naming, ProgressionConfiguration progression, RuntimeConfiguration runtime, DatabaseConfiguration database) {
        this(limits, naming, progression, runtime, database, new GuiConfiguration("Pet Menüsü", 6), new DiagnosticsConfiguration(100L), new DefinitionConfiguration("KEEP_OLD_ON_ANY_ERROR"), locale);
    }

    public record LimitsConfiguration(int maximumOwnedPets) {}
    public record NamingConfiguration(int minimumLength, int maximumLength, boolean allowColors, boolean allowFormatting) {}
    public record ProgressionConfiguration(boolean enabled, int maximumLevel) {}
    public record RuntimeConfiguration(long tickIntervalTicks, double startDistance, double stopDistance, double teleportDistance, double followSpeed) {}
    public record DatabaseConfiguration(boolean backupEnabled, boolean failOnBackupError, int maxBackups) {}
    public record GuiConfiguration(String title, int rows) {}
    public record DiagnosticsConfiguration(long slowQueryThresholdMs) {}
    public record DefinitionConfiguration(String reloadPolicy) {}
}

package com.petsistemi.progression;

import com.petsistemi.config.RuntimeConfigurationSnapshot;

import java.util.concurrent.atomic.AtomicReference;

public class ConfigBackedLinearExperienceCurve implements ExperienceCurve {

    private final AtomicReference<RuntimeConfigurationSnapshot> configSnapshot;
    private final long fallbackXpPerLevel;

    public ConfigBackedLinearExperienceCurve(AtomicReference<RuntimeConfigurationSnapshot> configSnapshot) {
        this.configSnapshot = configSnapshot;
        this.fallbackXpPerLevel = 100L;
    }

    public ConfigBackedLinearExperienceCurve(long fallbackXpPerLevel) {
        this.configSnapshot = null;
        this.fallbackXpPerLevel = Math.max(1L, fallbackXpPerLevel);
    }

    public ConfigBackedLinearExperienceCurve() {
        this(100L);
    }

    @Override
    public long getRequiredExperience(int level) {
        if (level <= 1) return 0L;
        RuntimeConfigurationSnapshot snapshot = (configSnapshot != null) ? configSnapshot.get() : null;
        long xpPerLevel = (snapshot != null && snapshot.configuration() != null && snapshot.configuration().progression() != null)
                ? snapshot.configuration().progression().xpPerLevel()
                : fallbackXpPerLevel;
        if (xpPerLevel < 1L) xpPerLevel = 1L;
        return (level - 1) * xpPerLevel;
    }

    @Override
    public int getLevelForExperience(long experience) {
        if (experience <= 0L) return 1;
        RuntimeConfigurationSnapshot snapshot = (configSnapshot != null) ? configSnapshot.get() : null;
        long xpPerLevel = (snapshot != null && snapshot.configuration() != null && snapshot.configuration().progression() != null)
                ? snapshot.configuration().progression().xpPerLevel()
                : fallbackXpPerLevel;
        if (xpPerLevel < 1L) xpPerLevel = 1L;
        return 1 + (int) (experience / xpPerLevel);
    }
}

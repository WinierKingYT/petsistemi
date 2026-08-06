package com.petsistemi.runtime;

import com.petsistemi.config.PluginConfiguration;
import com.petsistemi.config.RuntimeConfigurationSnapshot;
import com.petsistemi.domain.PetVector3;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Computes the effective visual scale of a display pet from its configured base
 * scale and its current level. When level scaling is enabled the pet grows by
 * {@code growthPerLevel} per level above 1, capped at {@code maxMultiplier}.
 */
public final class LevelScalePolicy {

    private LevelScalePolicy() {}

    public static PetVector3 compute(PetVector3 base, int level, boolean enabled, double growthPerLevel, double maxMultiplier) {
        if (base == null) {
            base = PetVector3.ONE;
        }
        if (!enabled || level <= 1) {
            return base;
        }
        double factor = 1.0 + (level - 1) * Math.max(0.0, growthPerLevel);
        factor = Math.min(factor, Math.max(1.0, maxMultiplier));
        return new PetVector3(base.x() * factor, base.y() * factor, base.z() * factor);
    }

    /** Snapshot-aware variant used by display representations; falls back to the base scale when no config is wired. */
    public static PetVector3 fromSnapshot(PetVector3 base, int level, AtomicReference<RuntimeConfigurationSnapshot> config) {
        if (config == null || config.get() == null || config.get().configuration() == null) {
            return base != null ? base : PetVector3.ONE;
        }
        PluginConfiguration.FeaturesConfiguration features = config.get().configuration().features();
        return compute(base, level, features.levelScalingEnabled(),
                features.levelScalingGrowthPerLevel(), features.levelScalingMaxMultiplier());
    }
}

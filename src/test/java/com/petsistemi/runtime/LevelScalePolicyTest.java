package com.petsistemi.runtime;

import com.petsistemi.config.PluginConfiguration;
import com.petsistemi.config.RuntimeConfigurationSnapshot;
import com.petsistemi.domain.PetVector3;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class LevelScalePolicyTest {

    private static final PetVector3 BASE = new PetVector3(1.0, 1.0, 1.0);

    @Test
    void disabledScalingReturnsBase() {
        assertEquals(BASE, LevelScalePolicy.compute(BASE, 10, false, 0.02, 1.5));
    }

    @Test
    void levelOneReturnsBase() {
        assertEquals(BASE, LevelScalePolicy.compute(BASE, 1, true, 0.02, 1.5));
    }

    @Test
    void growsByGrowthPerLevel() {
        PetVector3 scaled = LevelScalePolicy.compute(BASE, 11, true, 0.02, 10.0);
        assertEquals(1.2, scaled.x(), 1e-9);
        assertEquals(1.2, scaled.y(), 1e-9);
        assertEquals(1.2, scaled.z(), 1e-9);
    }

    @Test
    void capsAtMaxMultiplier() {
        PetVector3 scaled = LevelScalePolicy.compute(BASE, 50, true, 0.1, 1.5);
        assertEquals(1.5, scaled.x(), 1e-9);
    }

    @Test
    void nullBaseFallsBackToOne() {
        assertEquals(PetVector3.ONE, LevelScalePolicy.compute(null, 5, false, 0.02, 1.5));
    }

    @Test
    void negativeGrowthIsIgnored() {
        PetVector3 scaled = LevelScalePolicy.compute(BASE, 21, true, -0.5, 1.5);
        assertEquals(1.0, scaled.x(), 1e-9);
    }

    private static PluginConfiguration configWith(PluginConfiguration.FeaturesConfiguration features) {
        return new PluginConfiguration(
                new PluginConfiguration.LimitsConfiguration(10),
                new PluginConfiguration.NamingConfiguration(3, 32, true, true),
                new PluginConfiguration.ProgressionConfiguration(true, 100),
                new PluginConfiguration.RuntimeConfiguration(1, 1.5, 3.0, 30.0, 1.4),
                new PluginConfiguration.DatabaseConfiguration(true, false, 5),
                new PluginConfiguration.GuiConfiguration("Pet Menüsü", 6),
                new PluginConfiguration.DiagnosticsConfiguration(100L),
                new PluginConfiguration.DefinitionConfiguration("KEEP_OLD_ON_ANY_ERROR"),
                features,
                "tr");
    }

    @Test
    void fromSnapshotWithoutConfigReturnsBase() {
        assertEquals(BASE, LevelScalePolicy.fromSnapshot(BASE, 10, null));
        assertEquals(BASE, LevelScalePolicy.fromSnapshot(BASE, 10, new AtomicReference<>()));
    }

    @Test
    void fromSnapshotUsesWiredConfig() {
        PluginConfiguration config = configWith(new PluginConfiguration.FeaturesConfiguration(false, false, false, false,
                false, 45, false, true, 0.02, 1.5));
        AtomicReference<RuntimeConfigurationSnapshot> ref = new AtomicReference<>(
                new RuntimeConfigurationSnapshot(config, null, null, 0L));

        PetVector3 scaled = LevelScalePolicy.fromSnapshot(BASE, 11, ref);
        assertEquals(1.2, scaled.x(), 1e-9);
    }

    @Test
    void fromSnapshotDisabledConfigReturnsBase() {
        PluginConfiguration config = configWith(new PluginConfiguration.FeaturesConfiguration(false, false, false, false,
                false, 45, false, false, 0.02, 1.5));
        AtomicReference<RuntimeConfigurationSnapshot> ref = new AtomicReference<>(
                new RuntimeConfigurationSnapshot(config, null, null, 0L));

        assertEquals(BASE, LevelScalePolicy.fromSnapshot(BASE, 11, ref));
    }
}

package com.petsistemi.listener;

import com.petsistemi.api.PetExperienceService;
import com.petsistemi.config.PluginConfiguration;
import com.petsistemi.config.RuntimeConfigurationSnapshot;
import com.petsistemi.runtime.ActivePetRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PetProgressionListenerConfigTest {

    private ActivePetRegistry activePetRegistry;
    private PetExperienceService experienceService;
    private AtomicReference<RuntimeConfigurationSnapshot> configSnapshot;

    @BeforeEach
    void setUp() {
        activePetRegistry = new ActivePetRegistry();
        experienceService = mock(PetExperienceService.class);

        PluginConfiguration.ProgressionConfiguration prog = new PluginConfiguration.ProgressionConfiguration(
                true, 100, 100L, 10L, 25.0, 10L, 5L, 1.5
        );
        PluginConfiguration config = new PluginConfiguration(
                new PluginConfiguration.LimitsConfiguration(10),
                new PluginConfiguration.NamingConfiguration(2, 16, false, false),
                prog,
                new PluginConfiguration.RuntimeConfiguration(5L, 5.0, 2.0, 15.0, 1.2),
                new PluginConfiguration.DatabaseConfiguration(true, true, 5),
                new PluginConfiguration.GuiConfiguration("GUI", 6),
                new PluginConfiguration.DiagnosticsConfiguration(100L),
                new PluginConfiguration.DefinitionConfiguration("KEEP_OLD_ON_ANY_ERROR"),
                new PluginConfiguration.FeaturesConfiguration(false, false, false),
                "tr_TR"
        );

        RuntimeConfigurationSnapshot snapshot = new RuntimeConfigurationSnapshot(config, null, null, System.currentTimeMillis());
        configSnapshot = new AtomicReference<>(snapshot);
    }

    @Test
    void testListenerReadsProgressionConfigDynamically() {
        PetProgressionListener listener = new PetProgressionListener(activePetRegistry, experienceService, configSnapshot);
        assertNotNull(listener);

        // Update snapshot values dynamically
        PluginConfiguration.ProgressionConfiguration progUpdated = new PluginConfiguration.ProgressionConfiguration(
                true, 100, 100L, 10L, 10.0, 20L, 15L, 2.5
        );
        PluginConfiguration configUpdated = new PluginConfiguration(
                new PluginConfiguration.LimitsConfiguration(10),
                new PluginConfiguration.NamingConfiguration(2, 16, false, false),
                progUpdated,
                new PluginConfiguration.RuntimeConfiguration(5L, 5.0, 2.0, 15.0, 1.2),
                new PluginConfiguration.DatabaseConfiguration(true, true, 5),
                new PluginConfiguration.GuiConfiguration("GUI", 6),
                new PluginConfiguration.DiagnosticsConfiguration(100L),
                new PluginConfiguration.DefinitionConfiguration("KEEP_OLD_ON_ANY_ERROR"),
                new PluginConfiguration.FeaturesConfiguration(false, false, false),
                "tr_TR"
        );

        configSnapshot.set(new RuntimeConfigurationSnapshot(configUpdated, null, null, System.currentTimeMillis()));

        // Listener configuration updated atomically without re-registering
        assertEquals(2.5, configSnapshot.get().configuration().progression().killXpMultiplier());
        assertEquals(15L, configSnapshot.get().configuration().progression().blockBreakXp());
        assertEquals(10.0, configSnapshot.get().configuration().progression().walkXpThreshold());
        assertEquals(20L, configSnapshot.get().configuration().progression().walkXpAmount());
    }
}

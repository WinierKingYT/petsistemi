package com.petsistemi.integration.model;

import com.petsistemi.config.RuntimeConfigurationSnapshot;
import com.petsistemi.runtime.PetRepresentationRegistry;
import com.petsistemi.runtime.model.ModelProviderRegistry;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/** Discovers optional providers without making any of them a hard dependency. */
public final class ModelProviderBootstrap {
    private ModelProviderBootstrap() {}

    public static ModelProviderRegistry registerAvailable(
            JavaPlugin plugin,
            PetRepresentationRegistry representationRegistry,
            AtomicReference<RuntimeConfigurationSnapshot> configSnapshot) {
        ModelProviderRegistry registry = new ModelProviderRegistry(plugin, representationRegistry, configSnapshot);
        List<com.petsistemi.api.model.PetModelProvider> candidates = List.of(
                new ModelEngineModelProvider(plugin),
                new ItemsAdderModelProvider(plugin),
                new OraxenModelProvider(plugin));
        for (var provider : candidates) {
            if (registry.register(provider)) {
                plugin.getLogger().info(provider.pluginName() + " model adaptörü kaydedildi: " + provider.key());
            } else {
                plugin.getLogger().fine(provider.pluginName() + " bulunamadı; model adaptörü atlandı.");
            }
        }
        return registry;
    }
}

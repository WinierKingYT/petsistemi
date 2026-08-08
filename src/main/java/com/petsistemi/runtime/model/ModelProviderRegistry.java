package com.petsistemi.runtime.model;

import com.petsistemi.api.model.ModelProviderService;
import com.petsistemi.api.model.PetModelProvider;
import com.petsistemi.config.RuntimeConfigurationSnapshot;
import com.petsistemi.runtime.PetRepresentationRegistry;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/** Model-provider registry that also publishes providers as representation controllers. */
public final class ModelProviderRegistry implements ModelProviderService {
    private final JavaPlugin plugin;
    private final PetRepresentationRegistry representationRegistry;
    private final AtomicReference<RuntimeConfigurationSnapshot> configSnapshot;
    private final Map<NamespacedKey, PetModelProvider> providers = new LinkedHashMap<>();

    public ModelProviderRegistry(JavaPlugin plugin, PetRepresentationRegistry representationRegistry,
                                 AtomicReference<RuntimeConfigurationSnapshot> configSnapshot) {
        this.plugin = plugin;
        this.representationRegistry = representationRegistry;
        this.configSnapshot = configSnapshot;
    }

    @Override
    public synchronized boolean register(PetModelProvider provider) {
        if (provider == null || provider.key() == null || !provider.isAvailable()) return false;
        providers.put(provider.key(), provider);
        representationRegistry.register(provider.key(),
                new ProviderPetRepresentation(plugin, provider, configSnapshot));
        return true;
    }

    @Override
    public synchronized void unregister(NamespacedKey key) {
        if (key == null) return;
        providers.remove(key);
        representationRegistry.unregister(key);
    }

    @Override
    public synchronized Optional<PetModelProvider> find(NamespacedKey key) {
        return Optional.ofNullable(providers.get(key));
    }

    @Override
    public synchronized Set<NamespacedKey> registeredKeys() {
        return Collections.unmodifiableSet(Set.copyOf(providers.keySet()));
    }
}

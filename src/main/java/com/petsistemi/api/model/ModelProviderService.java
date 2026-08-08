package com.petsistemi.api.model;

import org.bukkit.NamespacedKey;

import java.util.Optional;
import java.util.Set;

/** Public registry for optional or third-party model providers. */
public interface ModelProviderService {
    boolean register(PetModelProvider provider);

    void unregister(NamespacedKey key);

    Optional<PetModelProvider> find(NamespacedKey key);

    Set<NamespacedKey> registeredKeys();
}

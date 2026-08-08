package com.petsistemi.runtime.model;

import com.petsistemi.api.model.PetModelHandle;
import com.petsistemi.api.model.PetModelProvider;
import com.petsistemi.domain.PetDefinition;
import com.petsistemi.domain.PetInstance;
import com.petsistemi.runtime.PetRepresentationRegistry;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ModelProviderRegistryTest {
    @Test
    void unavailableProviderIsSkippedWithoutPublishingRepresentation() {
        PetRepresentationRegistry representations = new PetRepresentationRegistry();
        ModelProviderRegistry registry = new ModelProviderRegistry(plugin(), representations, null);
        NamespacedKey key = new NamespacedKey("missing", "model");

        assertFalse(registry.register(provider(key, false)));
        assertTrue(registry.registeredKeys().isEmpty());
        assertNull(representations.get(key));
    }

    @Test
    void availableProviderPublishesAndUnregistersRepresentation() {
        PetRepresentationRegistry representations = new PetRepresentationRegistry();
        ModelProviderRegistry registry = new ModelProviderRegistry(plugin(), representations, null);
        NamespacedKey key = new NamespacedKey("example", "model");

        assertTrue(registry.register(provider(key, true)));
        assertTrue(registry.find(key).isPresent());
        assertNotNull(representations.get(key));

        registry.unregister(key);
        assertTrue(registry.find(key).isEmpty());
        assertNull(representations.get(key));
    }

    private static PetModelProvider provider(NamespacedKey key, boolean available) {
        return new PetModelProvider() {
            @Override public NamespacedKey key() { return key; }
            @Override public String pluginName() { return "Test"; }
            @Override public boolean isAvailable() { return available; }
            @Override public PetModelHandle spawn(PetInstance pet, PetDefinition definition, Player owner) { return null; }
        };
    }

    private static JavaPlugin plugin() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getName()).thenReturn("PetSistemi");
        return plugin;
    }
}

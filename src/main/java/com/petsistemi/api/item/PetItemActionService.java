package com.petsistemi.api.item;

import org.bukkit.NamespacedKey;

import java.util.Set;

/** Bukkit service extension point for third-party item actions. */
public interface PetItemActionService {
    void registerAction(NamespacedKey key, PetItemActionHandler handler);
    void unregisterAction(NamespacedKey key);
    Set<NamespacedKey> registeredActions();
}

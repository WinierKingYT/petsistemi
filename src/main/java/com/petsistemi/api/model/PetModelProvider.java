package com.petsistemi.api.model;

import com.petsistemi.domain.PetDefinition;
import com.petsistemi.domain.PetInstance;
import com.petsistemi.runtime.animation.PetAnimationTransition;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;

/**
 * Optional provider contract. Implementations isolate ModelEngine/ItemsAdder/Oraxen
 * APIs from core and are exposed through Bukkit's ServicesManager.
 */
public interface PetModelProvider {
    NamespacedKey key();

    String pluginName();

    /** False when the required plugin/API classes are absent or not enabled. */
    boolean isAvailable();

    PetModelHandle spawn(PetInstance pet, PetDefinition definition, Player owner);

    default void tick(PetModelHandle handle, PetInstance pet, PetDefinition definition, Player owner) {}

    default void updateVisual(PetModelHandle handle, PetInstance pet, PetDefinition definition) {}

    default void applyAnimation(PetModelHandle handle, PetAnimationTransition transition) {}

    default void remove(PetModelHandle handle) {
        if (handle != null && handle.entity().isValid()) handle.entity().remove();
    }
}

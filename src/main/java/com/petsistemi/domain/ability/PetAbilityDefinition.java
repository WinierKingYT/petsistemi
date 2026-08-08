package com.petsistemi.domain.ability;

import com.petsistemi.domain.behavior.PetBehaviorDefinition;
import org.bukkit.NamespacedKey;

import java.util.Objects;

/** An ability is a behavior with cooldown and target-selection metadata. */
public record PetAbilityDefinition(
        NamespacedKey key,
        int cooldownSeconds,
        AbilityTargetType targetType,
        double range,
        PetBehaviorDefinition behavior
) {
    public PetAbilityDefinition {
        Objects.requireNonNull(key, "ability key null olamaz");
        cooldownSeconds = Math.max(0, cooldownSeconds);
        targetType = targetType != null ? targetType : AbilityTargetType.NONE;
        range = Double.isFinite(range) && range > 0.0 ? range : 8.0;
        Objects.requireNonNull(behavior, "ability behavior null olamaz");
    }
}

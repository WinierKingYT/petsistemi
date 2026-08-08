package com.petsistemi.runtime.ability;

import org.bukkit.NamespacedKey;

public record AbilityOutcome(AbilityResult result, NamespacedKey ability, long remainingSeconds, int actionsExecuted) {
    public static AbilityOutcome of(AbilityResult result, NamespacedKey ability) {
        return new AbilityOutcome(result, ability, 0, 0);
    }
}

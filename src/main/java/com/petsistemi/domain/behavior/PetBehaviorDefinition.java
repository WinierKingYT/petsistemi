package com.petsistemi.domain.behavior;

import org.bukkit.NamespacedKey;

import java.util.List;
import java.util.Objects;

/** Immutable trigger → conditions → actions behavior pipeline. */
public record PetBehaviorDefinition(
        NamespacedKey trigger,
        boolean enabled,
        List<BehaviorConditionDefinition> conditions,
        List<BehaviorActionDefinition> actions
) {
    public PetBehaviorDefinition {
        Objects.requireNonNull(trigger, "trigger key null olamaz");
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
        actions = actions == null ? List.of() : List.copyOf(actions);
    }
}

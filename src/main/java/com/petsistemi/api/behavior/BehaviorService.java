package com.petsistemi.api.behavior;

import com.petsistemi.domain.behavior.PetBehaviorDefinition;
import com.petsistemi.runtime.behavior.BehaviorAction;
import com.petsistemi.runtime.behavior.BehaviorCondition;
import com.petsistemi.runtime.behavior.BehaviorContext;
import org.bukkit.NamespacedKey;

import java.util.List;
import java.util.Set;

/** Bukkit service entry point for third-party behavior extensions. */
public interface BehaviorService {
    void registerTrigger(NamespacedKey key);
    void registerCondition(NamespacedKey key, BehaviorCondition condition);
    void registerAction(NamespacedKey key, BehaviorAction action);
    int fire(NamespacedKey trigger, BehaviorContext context, List<PetBehaviorDefinition> definitions);
    Set<NamespacedKey> registeredTriggers();
    Set<NamespacedKey> registeredConditions();
    Set<NamespacedKey> registeredActions();
}

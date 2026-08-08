package com.petsistemi.runtime.behavior;

import com.petsistemi.api.behavior.BehaviorService;
import com.petsistemi.domain.behavior.BehaviorActionDefinition;
import com.petsistemi.domain.behavior.BehaviorConditionDefinition;
import com.petsistemi.domain.behavior.PetBehaviorDefinition;
import org.bukkit.NamespacedKey;

import java.util.List;

/** Open, deterministic trigger → condition → action executor. */
public final class PetBehaviorEngine implements BehaviorService {
    private final BehaviorTriggerRegistry triggers;
    private final BehaviorConditionRegistry conditions;
    private final BehaviorActionRegistry actions;

    public PetBehaviorEngine() {
        this(new BehaviorTriggerRegistry(), new BehaviorConditionRegistry(), new BehaviorActionRegistry());
    }

    public PetBehaviorEngine(BehaviorTriggerRegistry triggers, BehaviorConditionRegistry conditions,
                             BehaviorActionRegistry actions) {
        this.triggers = triggers;
        this.conditions = conditions;
        this.actions = actions;
    }

    public BehaviorTriggerRegistry triggers() { return triggers; }
    public BehaviorConditionRegistry conditions() { return conditions; }
    public BehaviorActionRegistry actions() { return actions; }

    @Override public void registerTrigger(NamespacedKey key) { triggers.register(key); }
    @Override public void registerCondition(NamespacedKey key, BehaviorCondition condition) { conditions.register(key, condition); }
    @Override public void registerAction(NamespacedKey key, BehaviorAction action) { actions.register(key, action); }
    @Override public java.util.Set<NamespacedKey> registeredTriggers() { return triggers.supportedKeys(); }
    @Override public java.util.Set<NamespacedKey> registeredConditions() { return conditions.supportedKeys(); }
    @Override public java.util.Set<NamespacedKey> registeredActions() { return actions.supportedKeys(); }

    @Override
    public int fire(NamespacedKey trigger, BehaviorContext context, List<PetBehaviorDefinition> definitions) {
        if (!triggers.contains(trigger) || context == null || definitions == null) return 0;
        int executed = 0;
        for (PetBehaviorDefinition definition : definitions) {
            if (definition == null || !definition.enabled() || !trigger.equals(definition.trigger())) continue;
            if (!matchesAll(context, definition.conditions())) continue;
            for (BehaviorActionDefinition actionDefinition : definition.actions()) {
                BehaviorAction action = actions.get(actionDefinition.key());
                if (action != null) {
                    action.execute(context, actionDefinition.parameters());
                    executed++;
                }
            }
        }
        return executed;
    }

    private boolean matchesAll(BehaviorContext context, List<BehaviorConditionDefinition> definitions) {
        for (BehaviorConditionDefinition definition : definitions) {
            BehaviorCondition condition = conditions.get(definition.key());
            if (condition == null || !condition.matches(context, definition.parameters())) return false;
        }
        return true;
    }
}

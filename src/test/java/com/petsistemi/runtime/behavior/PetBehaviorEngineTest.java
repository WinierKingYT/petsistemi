package com.petsistemi.runtime.behavior;

import com.petsistemi.domain.behavior.BehaviorActionDefinition;
import com.petsistemi.domain.behavior.BehaviorConditionDefinition;
import com.petsistemi.domain.behavior.PetBehaviorDefinition;
import org.bukkit.NamespacedKey;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PetBehaviorEngineTest {

    @Test
    void thirdPartyTriggerAndActionCanBeRegisteredAndExecuted() {
        PetBehaviorEngine engine = new PetBehaviorEngine();
        NamespacedKey trigger = new NamespacedKey("test", "owner_wave");
        NamespacedKey action = new NamespacedKey("test", "count");
        AtomicInteger calls = new AtomicInteger();
        engine.triggers().register(trigger);
        engine.actions().register(action, (context, parameters) -> calls.addAndGet((int) parameters.get("amount")));

        int executed = engine.fire(trigger, BehaviorContext.of(null, null), List.of(
                new PetBehaviorDefinition(trigger, true, List.of(),
                        List.of(new BehaviorActionDefinition(action, Map.of("amount", 2))))));

        assertEquals(1, executed);
        assertEquals(2, calls.get());
        assertTrue(engine.triggers().supportedKeys().contains(trigger));
        assertTrue(engine.actions().supportedKeys().contains(action));
    }

    @Test
    void actionsRunOnlyWhenEveryRegisteredConditionMatches() {
        PetBehaviorEngine engine = new PetBehaviorEngine();
        NamespacedKey trigger = new NamespacedKey("test", "trigger");
        NamespacedKey condition = new NamespacedKey("test", "allowed");
        NamespacedKey action = new NamespacedKey("test", "action");
        AtomicInteger calls = new AtomicInteger();
        engine.triggers().register(trigger);
        engine.conditions().register(condition,
                (context, parameters) -> Boolean.TRUE.equals(context.attributes().get("allowed")));
        engine.actions().register(action, (context, parameters) -> calls.incrementAndGet());
        PetBehaviorDefinition behavior = new PetBehaviorDefinition(trigger, true,
                List.of(new BehaviorConditionDefinition(condition, Map.of())),
                List.of(new BehaviorActionDefinition(action, Map.of())));

        assertEquals(0, engine.fire(trigger, new BehaviorContext(null, null, Map.of("allowed", false)), List.of(behavior)));
        assertEquals(1, engine.fire(trigger, new BehaviorContext(null, null, Map.of("allowed", true)), List.of(behavior)));
        assertEquals(1, calls.get());
    }

    @Test
    void unknownTriggerDoesNotExecuteActions() {
        PetBehaviorEngine engine = new PetBehaviorEngine();
        NamespacedKey trigger = new NamespacedKey("test", "unknown");
        NamespacedKey action = new NamespacedKey("test", "action");
        AtomicInteger calls = new AtomicInteger();
        engine.actions().register(action, (context, parameters) -> calls.incrementAndGet());

        int executed = engine.fire(trigger, BehaviorContext.of(null, null), List.of(
                new PetBehaviorDefinition(trigger, true, List.of(),
                        List.of(new BehaviorActionDefinition(action, Map.of())))));

        assertEquals(0, executed);
        assertEquals(0, calls.get());
    }
}

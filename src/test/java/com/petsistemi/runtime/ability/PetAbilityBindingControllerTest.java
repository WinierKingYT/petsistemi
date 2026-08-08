package com.petsistemi.runtime.ability;

import com.petsistemi.definition.PetDefinitionRegistry;
import com.petsistemi.domain.PetDefinition;
import com.petsistemi.domain.PetRuntimeState;
import com.petsistemi.domain.ability.AbilityTargetType;
import com.petsistemi.domain.ability.PetAbilityDefinition;
import com.petsistemi.domain.behavior.BehaviorActionDefinition;
import com.petsistemi.domain.behavior.PetBehaviorDefinition;
import com.petsistemi.runtime.ActivePet;
import com.petsistemi.runtime.ActivePetRegistry;
import com.petsistemi.runtime.behavior.BuiltInBehaviorKeys;
import com.petsistemi.runtime.behavior.PetBehaviorEngine;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PetAbilityBindingControllerTest {
    @Test
    void boundAbilityUsesTheSharedAbilityEngine() {
        NamespacedKey key = new NamespacedKey("petsistemi", "pulse");
        NamespacedKey action = new NamespacedKey("test", "count");
        AtomicInteger calls = new AtomicInteger();
        PetBehaviorEngine behaviors = new PetBehaviorEngine();
        behaviors.registerAction(action, (context, parameters) -> calls.incrementAndGet());
        PetAbilityEngine engine = new PetAbilityEngine(behaviors);
        PetAbilityDefinition ability = new PetAbilityDefinition(key, 0, AbilityTargetType.OWNER, 8,
                new PetBehaviorDefinition(BuiltInBehaviorKeys.ABILITY, true, List.of(),
                        List.of(new BehaviorActionDefinition(action, Map.of()))));
        PetDefinition definition = PetDefinition.builder("mage", "Mage").abilities(Map.of(key, ability)).build();
        Player owner = mock(Player.class);
        UUID ownerId = UUID.randomUUID();
        when(owner.getUniqueId()).thenReturn(ownerId);
        ActivePet active = new ActivePet(UUID.randomUUID(), ownerId, "mage", 1, UUID.randomUUID(),
                mock(Entity.class), PetRuntimeState.ACTIVE);
        ActivePetRegistry activeRegistry = new ActivePetRegistry();
        activeRegistry.register(active);
        PetDefinitionRegistry definitions = mock(PetDefinitionRegistry.class);
        when(definitions.find("mage")).thenReturn(Optional.of(definition));
        PetAbilityBindingController bindings = new PetAbilityBindingController(engine, activeRegistry, definitions);

        assertTrue(bindings.bind(ownerId, definition, "pulse"));
        assertEquals(key, bindings.binding(ownerId).orElseThrow());
        assertEquals(AbilityResult.ACTIVATED, bindings.activateBound(owner).result());
        assertEquals(1, calls.get());

        bindings.unbind(ownerId);
        assertTrue(bindings.binding(ownerId).isEmpty());
    }
}

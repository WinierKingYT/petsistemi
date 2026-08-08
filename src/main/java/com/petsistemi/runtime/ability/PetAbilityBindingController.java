package com.petsistemi.runtime.ability;

import com.petsistemi.definition.PetDefinitionRegistry;
import com.petsistemi.domain.PetDefinition;
import com.petsistemi.runtime.ActivePet;
import com.petsistemi.runtime.ActivePetRegistry;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Session-scoped binding for sneak + swap-hand to one ability. */
public final class PetAbilityBindingController {
    private final PetAbilityEngine abilityEngine;
    private final ActivePetRegistry activeRegistry;
    private final PetDefinitionRegistry definitionRegistry;
    private final Map<UUID, NamespacedKey> bindings = new HashMap<>();

    public PetAbilityBindingController(PetAbilityEngine abilityEngine, ActivePetRegistry activeRegistry,
                                       PetDefinitionRegistry definitionRegistry) {
        this.abilityEngine = abilityEngine;
        this.activeRegistry = activeRegistry;
        this.definitionRegistry = definitionRegistry;
    }

    public boolean bind(UUID ownerId, PetDefinition definition, String rawAbility) {
        if (ownerId == null || abilityEngine == null) return false;
        NamespacedKey key = abilityEngine.findAbilityKey(definition, rawAbility);
        if (key == null) return false;
        bindings.put(ownerId, key);
        return true;
    }

    public void unbind(UUID ownerId) { if (ownerId != null) bindings.remove(ownerId); }
    public Optional<NamespacedKey> binding(UUID ownerId) { return Optional.ofNullable(bindings.get(ownerId)); }

    public AbilityOutcome activateBound(Player owner) {
        if (owner == null || activeRegistry == null || definitionRegistry == null) {
            return AbilityOutcome.of(AbilityResult.INVALID_CONTEXT, null);
        }
        NamespacedKey key = bindings.get(owner.getUniqueId());
        if (key == null) return AbilityOutcome.of(AbilityResult.UNKNOWN_ABILITY, null);
        ActivePet active = activeRegistry.getByOwner(owner.getUniqueId()).orElse(null);
        if (active == null) return AbilityOutcome.of(AbilityResult.INVALID_CONTEXT, key);
        PetDefinition definition = definitionRegistry.find(active.getDefinitionId()).orElse(null);
        return abilityEngine.activate(owner, active, definition, key);
    }
}

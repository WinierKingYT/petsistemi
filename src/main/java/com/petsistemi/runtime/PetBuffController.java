package com.petsistemi.runtime;

import com.petsistemi.domain.PetBuffDefinition;
import com.petsistemi.domain.PetDefinition;
import com.petsistemi.domain.RuntimeKeyResolver;
import com.petsistemi.domain.behavior.PetBehaviorDefinition;
import com.petsistemi.runtime.behavior.BehaviorContext;
import com.petsistemi.runtime.behavior.BuiltInBehaviorKeys;
import com.petsistemi.runtime.behavior.LegacyBehaviorAdapter;
import com.petsistemi.runtime.behavior.PetBehaviorEngine;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;

/**
 * Periodically applies configured passive potion effects (buffs) to owners of active pets.
 */
public class PetBuffController {

    private final boolean enabled;
    private final PetBehaviorEngine behaviorEngine;

    /** Buffs on, which is the shipped default — each pet still opts in via its own {@code buffs:}. */
    public PetBuffController() {
        this(true);
    }

    /**
     * @param enabled server-wide kill switch ({@code features.buffs.enabled}); when off, no pet
     *                grants potion effects no matter what its definition declares.
     */
    public PetBuffController(boolean enabled) {
        this(enabled, new PetBehaviorEngine());
    }

    public PetBuffController(boolean enabled, PetBehaviorEngine behaviorEngine) {
        this.enabled = enabled;
        this.behaviorEngine = behaviorEngine != null ? behaviorEngine : new PetBehaviorEngine();
        this.behaviorEngine.triggers().register(BuiltInBehaviorKeys.TICK);
        this.behaviorEngine.conditions().register(BuiltInBehaviorKeys.MIN_LEVEL, this::hasMinimumLevel);
        this.behaviorEngine.actions().register(BuiltInBehaviorKeys.APPLY_POTION_EFFECT, this::applyPotionEffect);
    }

    /**
     * Applies one pet's passive buffs to its owner.
     *
     * <p>Deliberately per-pet rather than a self-driven sweep: the runtime tick loop
     * isolates each pet in its own try/catch, so a buff failure on one pet must not be
     * able to abort the tick for everyone else.</p>
     */
    public void apply(ActivePet pet, Player owner, PetDefinition definition) {
        if (!enabled || pet == null || owner == null || definition == null || !owner.isOnline()) {
            return;
        }

        List<PetBehaviorDefinition> definitions = new ArrayList<>();
        if (definition.behaviors() != null) definitions.addAll(definition.behaviors());
        List<PetBuffDefinition> buffs = definition.buffs();
        if (buffs != null) {
            for (PetBuffDefinition buff : buffs) {
                if (buff != null && buff.effectType() != null) definitions.add(LegacyBehaviorAdapter.buff(buff));
            }
        }
        behaviorEngine.fire(BuiltInBehaviorKeys.TICK,
                new BehaviorContext(pet.getSpawnedEntity(), definition, Map.of("pet", pet, "owner", owner)),
                definitions);
    }

    private boolean hasMinimumLevel(BehaviorContext context, Map<String, Object> parameters) {
        Object petValue = context.attributes().get("pet");
        Object levelValue = parameters.get("level");
        return petValue instanceof ActivePet pet
                && pet.getLevel() >= (levelValue instanceof Number number ? number.intValue() : 1);
    }

    private void applyPotionEffect(BehaviorContext context, Map<String, Object> parameters) {
        Object ownerValue = context.attributes().get("owner");
        if (!(ownerValue instanceof Player owner)) return;
        org.bukkit.potion.PotionEffectType type = RuntimeKeyResolver.potionEffect(string(parameters, "effect"));
        if (type == null) return;
        int duration = integer(parameters, "duration-ticks", 60);
        int amplifier = integer(parameters, "amplifier", 0);
        // Passive buffs are refreshed while the pet is active: keep the icon, hide swirls.
        owner.addPotionEffect(new PotionEffect(type, duration, amplifier, true, false, true));
    }

    private static String string(Map<String, Object> parameters, String key) {
        Object value = parameters.get(key);
        return value != null ? value.toString() : null;
    }

    private static int integer(Map<String, Object> parameters, String key, int fallback) {
        Object value = parameters.get(key);
        return value instanceof Number number ? number.intValue() : fallback;
    }
}

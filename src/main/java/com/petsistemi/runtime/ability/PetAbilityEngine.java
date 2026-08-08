package com.petsistemi.runtime.ability;

import com.petsistemi.api.behavior.BehaviorService;
import com.petsistemi.domain.PetDefinition;
import com.petsistemi.domain.ability.AbilityTargetType;
import com.petsistemi.domain.ability.PetAbilityDefinition;
import com.petsistemi.runtime.ActivePet;
import com.petsistemi.runtime.behavior.BehaviorContext;
import com.petsistemi.runtime.behavior.BuiltInBehaviorKeys;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;

/** Executes behavior-backed abilities with per-owner/per-ability cooldowns. */
public final class PetAbilityEngine {
    private final BehaviorService behaviorService;
    private final LongSupplier clock;
    private final Map<UUID, Map<NamespacedKey, Long>> cooldowns = new HashMap<>();

    public PetAbilityEngine(BehaviorService behaviorService) {
        this(behaviorService, System::currentTimeMillis);
    }

    public PetAbilityEngine(BehaviorService behaviorService, LongSupplier clock) {
        this.behaviorService = behaviorService;
        this.clock = clock != null ? clock : System::currentTimeMillis;
        if (behaviorService != null) {
            behaviorService.registerTrigger(BuiltInBehaviorKeys.ABILITY);
            BuiltInAbilityActions.register(behaviorService);
        }
    }

    public AbilityOutcome activate(Player owner, ActivePet activePet, PetDefinition definition, String rawAbility) {
        NamespacedKey key = findAbilityKey(definition, rawAbility);
        if (key == null) return AbilityOutcome.of(AbilityResult.UNKNOWN_ABILITY, null);
        return activate(owner, activePet, definition, key);
    }

    public AbilityOutcome activate(Player owner, ActivePet activePet, PetDefinition definition, NamespacedKey key) {
        if (owner == null || activePet == null || definition == null || behaviorService == null || key == null) {
            return AbilityOutcome.of(AbilityResult.INVALID_CONTEXT, key);
        }
        PetAbilityDefinition ability = definition.abilities() != null ? definition.abilities().get(key) : null;
        if (ability == null) return AbilityOutcome.of(AbilityResult.UNKNOWN_ABILITY, key);

        long now = clock.getAsLong();
        Long lastUse = cooldown(owner.getUniqueId()).get(key);
        long cooldownMillis = ability.cooldownSeconds() * 1000L;
        if (lastUse != null && cooldownMillis > 0L) {
            long remaining = lastUse + cooldownMillis - now;
            if (remaining > 0L) {
                return new AbilityOutcome(AbilityResult.COOLDOWN, key, (remaining + 999L) / 1000L, 0);
            }
        }

        TargetSelection selection = selectTargets(owner, activePet, ability.targetType(), ability.range());
        if (selection.required() && selection.targets().isEmpty()) {
            return AbilityOutcome.of(AbilityResult.NO_TARGET, key);
        }
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("owner", owner);
        attributes.put("pet", activePet);
        attributes.put("ability", ability);
        attributes.put("targets", selection.targets());
        if (!selection.targets().isEmpty()) attributes.put("target", selection.targets().get(0));

        int executed = behaviorService.fire(BuiltInBehaviorKeys.ABILITY,
                new BehaviorContext(activePet.getSpawnedEntity(), definition, attributes),
                List.of(ability.behavior()));
        if (executed == 0) return AbilityOutcome.of(AbilityResult.NO_REGISTERED_ACTION, key);
        if (cooldownMillis > 0L) cooldown(owner.getUniqueId()).put(key, now);
        return new AbilityOutcome(AbilityResult.ACTIVATED, key, 0, executed);
    }

    public void cleanup(UUID ownerId) {
        if (ownerId != null) cooldowns.remove(ownerId);
    }

    private TargetSelection selectTargets(Player owner, ActivePet activePet, AbilityTargetType type, double range) {
        if (type == AbilityTargetType.NONE) return new TargetSelection(false, List.of());
        Entity pet = activePet.getSpawnedEntity();
        if (type == AbilityTargetType.PET) return new TargetSelection(true, pet != null ? List.of(pet) : List.of());
        if (type == AbilityTargetType.OWNER) return new TargetSelection(true, List.of(owner));
        if (type == AbilityTargetType.OWNER_TARGET) {
            Entity target = owner.getTargetEntity(Math.max(1, (int) Math.ceil(range)));
            return new TargetSelection(true, target != null ? List.of(target) : List.of());
        }
        if (pet == null) return new TargetSelection(true, List.of());
        List<Entity> nearby = new ArrayList<>();
        for (Entity entity : pet.getNearbyEntities(range, range, range)) {
            if (entity instanceof LivingEntity && entity != owner && entity != pet) nearby.add(entity);
        }
        nearby.sort(Comparator.comparingDouble(entity -> entity.getLocation().distanceSquared(pet.getLocation())));
        if (type == AbilityTargetType.NEAREST_LIVING && !nearby.isEmpty()) {
            return new TargetSelection(true, List.of(nearby.get(0)));
        }
        return new TargetSelection(true, List.copyOf(nearby));
    }

    public NamespacedKey findAbilityKey(PetDefinition definition, String raw) {
        if (definition == null || definition.abilities() == null || raw == null || raw.isBlank()) return null;
        NamespacedKey exact = raw.contains(":") ? NamespacedKey.fromString(raw.toLowerCase(java.util.Locale.ROOT))
                : NamespacedKey.fromString("petsistemi:" + raw.toLowerCase(java.util.Locale.ROOT));
        if (exact != null && definition.abilities().containsKey(exact)) return exact;
        return definition.abilities().keySet().stream()
                .filter(key -> key.getKey().equalsIgnoreCase(raw) || key.toString().equalsIgnoreCase(raw))
                .findFirst().orElse(null);
    }

    private Map<NamespacedKey, Long> cooldown(UUID ownerId) {
        return cooldowns.computeIfAbsent(ownerId, ignored -> new HashMap<>());
    }

    private record TargetSelection(boolean required, List<Entity> targets) {}
}

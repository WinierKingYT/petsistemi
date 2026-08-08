package com.petsistemi.runtime.ability;

import com.petsistemi.domain.PetDefinition;
import com.petsistemi.domain.PetRuntimeState;
import com.petsistemi.domain.ability.AbilityTargetType;
import com.petsistemi.domain.ability.PetAbilityDefinition;
import com.petsistemi.domain.behavior.BehaviorActionDefinition;
import com.petsistemi.domain.behavior.PetBehaviorDefinition;
import com.petsistemi.runtime.ActivePet;
import com.petsistemi.runtime.behavior.BuiltInBehaviorKeys;
import com.petsistemi.runtime.behavior.PetBehaviorEngine;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Snowball;
import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PetAbilityEngineTest {
    private final NamespacedKey abilityKey = new NamespacedKey("petsistemi", "arcane_burst");
    private final NamespacedKey actionKey = new NamespacedKey("test", "record_target");
    private final AtomicLong clock = new AtomicLong(1_000L);
    private final AtomicReference<Entity> recordedTarget = new AtomicReference<>();
    private PetBehaviorEngine behaviors;
    private PetAbilityEngine engine;
    private Player owner;
    private ActivePet active;
    private Entity petEntity;

    @BeforeEach
    void setUp() {
        behaviors = new PetBehaviorEngine();
        behaviors.registerAction(actionKey,
                (context, parameters) -> recordedTarget.set((Entity) context.attributes().get("target")));
        engine = new PetAbilityEngine(behaviors, clock::get);
        owner = mock(Player.class);
        when(owner.getUniqueId()).thenReturn(UUID.randomUUID());
        petEntity = mock(Entity.class);
        active = new ActivePet(UUID.randomUUID(), owner.getUniqueId(), "mage", 1,
                UUID.randomUUID(), petEntity, PetRuntimeState.ACTIVE);
    }

    private PetDefinition definition(AbilityTargetType targetType, int cooldownSeconds) {
        PetBehaviorDefinition behavior = new PetBehaviorDefinition(BuiltInBehaviorKeys.ABILITY, true,
                List.of(), List.of(new BehaviorActionDefinition(actionKey, Map.of())));
        PetAbilityDefinition ability = new PetAbilityDefinition(abilityKey, cooldownSeconds,
                targetType, 12.0, behavior);
        return PetDefinition.builder("mage", "Mage").abilities(Map.of(abilityKey, ability)).build();
    }

    @Test
    void ownerTargetIsPassedToBehaviorAction() {
        Entity target = mock(Entity.class);
        when(owner.getTargetEntity(12)).thenReturn(target);

        AbilityOutcome outcome = engine.activate(owner, active, definition(AbilityTargetType.OWNER_TARGET, 5), abilityKey);

        assertEquals(AbilityResult.ACTIVATED, outcome.result());
        assertEquals(1, outcome.actionsExecuted());
        assertSame(target, recordedTarget.get());
    }

    @Test
    void cooldownStartsOnlyAfterSuccessfulExecution() {
        PetDefinition definition = definition(AbilityTargetType.OWNER, 5);

        assertEquals(AbilityResult.ACTIVATED, engine.activate(owner, active, definition, "arcane_burst").result());
        AbilityOutcome blocked = engine.activate(owner, active, definition, "arcane_burst");
        assertEquals(AbilityResult.COOLDOWN, blocked.result());
        assertEquals(5, blocked.remainingSeconds());

        clock.addAndGet(5_000L);
        assertEquals(AbilityResult.ACTIVATED, engine.activate(owner, active, definition, "arcane_burst").result());
    }

    @Test
    void missingRequiredTargetDoesNotConsumeCooldown() {
        PetDefinition definition = definition(AbilityTargetType.OWNER_TARGET, 10);
        when(owner.getTargetEntity(12)).thenReturn(null);

        assertEquals(AbilityResult.NO_TARGET, engine.activate(owner, active, definition, abilityKey).result());
        Entity target = mock(Entity.class);
        when(owner.getTargetEntity(12)).thenReturn(target);
        assertEquals(AbilityResult.ACTIVATED, engine.activate(owner, active, definition, abilityKey).result());
    }

    @Test
    void unknownAbilityIsReportedWithoutExecution() {
        AbilityOutcome outcome = engine.activate(owner, active, definition(AbilityTargetType.OWNER, 5), "missing");
        assertEquals(AbilityResult.UNKNOWN_ABILITY, outcome.result());
    }

    @Test
    void builtInProjectileActionLaunchesTowardSelectedTarget() {
        NamespacedKey projectileAbilityKey = new NamespacedKey("petsistemi", "snowball");
        PetBehaviorDefinition behavior = new PetBehaviorDefinition(BuiltInBehaviorKeys.ABILITY, true,
                List.of(), List.of(new BehaviorActionDefinition(BuiltInBehaviorKeys.LAUNCH_PROJECTILE,
                        Map.of("projectile", "SNOWBALL", "speed", 2.0))));
        PetAbilityDefinition ability = new PetAbilityDefinition(projectileAbilityKey, 0,
                AbilityTargetType.OWNER_TARGET, 12.0, behavior);
        PetDefinition definition = PetDefinition.builder("mage", "Mage")
                .abilities(Map.of(projectileAbilityKey, ability)).build();
        LivingEntity target = mock(LivingEntity.class);
        Snowball projectile = mock(Snowball.class);
        when(owner.getTargetEntity(12)).thenReturn(target);
        when(owner.launchProjectile(Snowball.class)).thenReturn(projectile);
        when(owner.getEyeLocation()).thenReturn(new Location(null, 0, 0, 0));
        when(target.getEyeLocation()).thenReturn(new Location(null, 0, 0, 5));

        AbilityOutcome outcome = engine.activate(owner, active, definition, projectileAbilityKey);

        assertEquals(AbilityResult.ACTIVATED, outcome.result());
        org.mockito.Mockito.verify(projectile).setVelocity(new org.bukkit.util.Vector(0, 0, 2));
    }

    @Test
    void builtInAreaDamageActionAffectsEverySelectedLivingTarget() {
        NamespacedKey areaKey = new NamespacedKey("petsistemi", "shockwave");
        PetBehaviorDefinition behavior = new PetBehaviorDefinition(BuiltInBehaviorKeys.ABILITY, true,
                List.of(), List.of(new BehaviorActionDefinition(BuiltInBehaviorKeys.DAMAGE_TARGETS,
                        Map.of("amount", 3.5))));
        PetAbilityDefinition ability = new PetAbilityDefinition(areaKey, 0,
                AbilityTargetType.AREA_AROUND_PET, 6.0, behavior);
        PetDefinition definition = PetDefinition.builder("mage", "Mage")
                .abilities(Map.of(areaKey, ability)).build();
        LivingEntity first = mock(LivingEntity.class);
        LivingEntity second = mock(LivingEntity.class);
        when(petEntity.getNearbyEntities(6.0, 6.0, 6.0)).thenReturn(List.of(first, second));
        World world = mock(World.class);
        when(petEntity.getLocation()).thenReturn(new Location(world, 0, 0, 0));
        when(first.getLocation()).thenReturn(new Location(world, 1, 0, 0));
        when(second.getLocation()).thenReturn(new Location(world, 2, 0, 0));

        assertEquals(AbilityResult.ACTIVATED, engine.activate(owner, active, definition, areaKey).result());
        org.mockito.Mockito.verify(first).damage(3.5, owner);
        org.mockito.Mockito.verify(second).damage(3.5, owner);
    }
}

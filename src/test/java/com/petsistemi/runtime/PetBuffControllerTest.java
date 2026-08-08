package com.petsistemi.runtime;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import com.petsistemi.domain.PetBuffDefinition;
import com.petsistemi.domain.PetDefinition;
import com.petsistemi.domain.PetRuntimeState;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.Map;
import org.bukkit.NamespacedKey;
import com.petsistemi.domain.behavior.BehaviorActionDefinition;
import com.petsistemi.domain.behavior.BehaviorConditionDefinition;
import com.petsistemi.domain.behavior.PetBehaviorDefinition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Buffs are the only mechanical reward a pet gives its owner, and level tiers are expressed
 * as several entries for the same effect gated by {@code min-level}. Picking the wrong entry
 * either denies a level-30 owner the strength they earned or hands a level-1 owner the
 * maximum tier.
 */
class PetBuffControllerTest {

    private ServerMock server;
    private PetBuffController controller;
    private PlayerMock owner;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        controller = new PetBuffController();
        owner = server.addPlayer("Eleven");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private static ActivePet petAtLevel(int level) {
        return new ActivePet(UUID.randomUUID(), UUID.randomUUID(), "wolf", level,
                UUID.randomUUID(), null, PetRuntimeState.ACTIVE);
    }

    private static PetDefinition withBuffs(PetBuffDefinition... buffs) {
        return PetDefinition.builder("wolf", "Kurt").buffs(List.of(buffs)).build();
    }

    private static PetBuffDefinition speed(int amplifier, int minLevel) {
        return new PetBuffDefinition(PotionEffectType.SPEED, amplifier, minLevel, 70);
    }

    @Test
    void aBuffAtOrAboveItsMinimumLevelIsApplied() {
        controller.apply(petAtLevel(1), owner, withBuffs(speed(0, 1)));

        PotionEffect effect = owner.getPotionEffect(PotionEffectType.SPEED);
        assertNotNull(effect, "seviye yeterliyken buff uygulanmalı");
        assertEquals(0, effect.getAmplifier());
    }

    @Test
    void aBuffBelowItsMinimumLevelIsNotApplied() {
        controller.apply(petAtLevel(3), owner, withBuffs(speed(1, 6)));

        assertFalse(owner.hasPotionEffect(PotionEffectType.SPEED), "seviye yetmiyorken buff verilmemeli");
    }

    /**
     * Level tiers are written as several entries for one effect. Only the strongest entry the
     * pet has earned may win, no matter how the admin ordered them in the file.
     */
    @Test
    void theStrongestEarnedTierWinsRegardlessOfOrder() {
        controller.apply(petAtLevel(12), owner,
                withBuffs(speed(0, 1), speed(1, 6), speed(2, 11), speed(3, 16)));

        assertEquals(2, owner.getPotionEffect(PotionEffectType.SPEED).getAmplifier(),
                "seviye 12'de kademe 2 kazanılmış olmalı");
    }

    @Test
    void tierSelectionIsIndependentOfDeclarationOrder() {
        controller.apply(petAtLevel(12), owner,
                withBuffs(speed(3, 16), speed(2, 11), speed(1, 6), speed(0, 1)));

        assertEquals(2, owner.getPotionEffect(PotionEffectType.SPEED).getAmplifier(),
                "ters sırada yazılmış kademeler aynı sonucu vermeli");
    }

    @Test
    void differentEffectsAreAppliedIndependently() {
        controller.apply(petAtLevel(20), owner, withBuffs(
                speed(1, 1),
                new PetBuffDefinition(PotionEffectType.NIGHT_VISION, 0, 1, 300)));

        assertTrue(owner.hasPotionEffect(PotionEffectType.SPEED));
        assertTrue(owner.hasPotionEffect(PotionEffectType.NIGHT_VISION));
    }

    @Test
    void aPetWithoutBuffsGrantsNothing() {
        controller.apply(petAtLevel(50), owner, PetDefinition.builder("wolf", "Kurt").build());

        assertTrue(owner.getActivePotionEffects().isEmpty(), "buff bildirmeyen pet etki vermemeli");
    }

    /**
     * Pet buffs refresh forever, so visible swirls would follow the owner permanently.
     * The inventory icon still has to show, otherwise the effect looks like a bug to the
     * player who cannot tell where their speed is coming from.
     */
    @Test
    void permanentBuffsShowAnIconButNoParticles() {
        controller.apply(petAtLevel(1), owner, withBuffs(speed(0, 1)));

        PotionEffect effect = owner.getPotionEffect(PotionEffectType.SPEED);
        assertFalse(effect.hasParticles(), "kalıcı buff parçacık saçmamalı");
        assertTrue(effect.hasIcon(), "etki envanterde görünmeli");
    }

    /** {@code features.buffs.enabled: false} must silence every pet, however it is defined. */
    @Test
    void theServerWideKillSwitchSilencesEveryPet() {
        new PetBuffController(false).apply(petAtLevel(50), owner, withBuffs(speed(3, 1)));

        assertTrue(owner.getActivePotionEffects().isEmpty(), "kapatıldığında hiçbir etki verilmemeli");
    }

    /** An offline owner must never be touched — the pet may outlive the session briefly. */
    @Test
    void nullArgumentsAreIgnored() {
        controller.apply(null, owner, withBuffs(speed(0, 1)));
        controller.apply(petAtLevel(1), null, withBuffs(speed(0, 1)));
        controller.apply(petAtLevel(1), owner, null);

        assertTrue(owner.getActivePotionEffects().isEmpty());
    }

    @Test
    void nativeTickBehaviorCanApplyAPotionEffect() {
        PetBehaviorDefinition behavior = new PetBehaviorDefinition(
                new NamespacedKey("petsistemi", "tick"), true,
                List.of(new BehaviorConditionDefinition(new NamespacedKey("petsistemi", "min_level"),
                        Map.of("level", 4))),
                List.of(new BehaviorActionDefinition(new NamespacedKey("petsistemi", "apply_potion_effect"),
                        Map.of("effect", "SPEED", "amplifier", 2, "duration-ticks", 90))));
        PetDefinition definition = PetDefinition.builder("native", "Native")
                .behaviors(List.of(behavior)).build();

        controller.apply(petAtLevel(4), owner, definition);

        PotionEffect effect = owner.getPotionEffect(PotionEffectType.SPEED);
        assertNotNull(effect);
        assertEquals(2, effect.getAmplifier());
        assertEquals(90, effect.getDuration());
    }
}

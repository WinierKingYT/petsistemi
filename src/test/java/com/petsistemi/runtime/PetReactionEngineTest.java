package com.petsistemi.runtime;

import com.petsistemi.config.PluginConfiguration;
import com.petsistemi.config.RuntimeConfigurationSnapshot;
import com.petsistemi.domain.PetDefinition;
import com.petsistemi.domain.PetEmoteDefinition;
import com.petsistemi.domain.PetReactionDefinition;
import com.petsistemi.domain.PetReactionType;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.mockito.Mockito.*;

class PetReactionEngineTest {

    private AtomicReference<RuntimeConfigurationSnapshot> configRef;
    private PetReactionEngine engine;
    private World world;
    private Entity pet;

    @BeforeEach
    void setUp() {
        configRef = new AtomicReference<>(snapshot(true));
        engine = new PetReactionEngine(configRef);
        world = mock(World.class);
        pet = mock(Entity.class);
        when(pet.isValid()).thenReturn(true);
        when(pet.getWorld()).thenReturn(world);
        when(pet.getLocation()).thenReturn(new Location(world, 1, 2, 3));
    }

    private static RuntimeConfigurationSnapshot snapshot(boolean reactionsEnabled) {
        PluginConfiguration config = new PluginConfiguration(
                new PluginConfiguration.LimitsConfiguration(10),
                new PluginConfiguration.NamingConfiguration(3, 32, true, true),
                new PluginConfiguration.ProgressionConfiguration(true, 100),
                new PluginConfiguration.RuntimeConfiguration(1, 1.5, 3.0, 30.0, 1.4),
                new PluginConfiguration.DatabaseConfiguration(true, false, 5),
                new PluginConfiguration.GuiConfiguration("Pet Menüsü", 6),
                new PluginConfiguration.DiagnosticsConfiguration(100L),
                new PluginConfiguration.DefinitionConfiguration("KEEP_OLD_ON_ANY_ERROR"),
                new PluginConfiguration.FeaturesConfiguration(false, false, false, false,
                        false, 45, reactionsEnabled, false, 0.02, 1.5),
                "tr");
        return new RuntimeConfigurationSnapshot(config, null, null, 0L);
    }

    @Test
    void ownerDamagePlaysGrowlAndAngryParticle() {
        engine.playOwnerDamage(pet);

        verify(world).playSound(any(Location.class), eq(Sound.ENTITY_WOLF_GROWL), anyFloat(), anyFloat());
        verify(world).spawnParticle(eq(Particle.VILLAGER_ANGRY), any(Location.class), eq(3), anyDouble(), anyDouble(), anyDouble(), anyDouble());
    }

    @Test
    void levelUpPlaysHappyParticle() {
        engine.playLevelUp(pet);

        verify(world).spawnParticle(eq(Particle.VILLAGER_HAPPY), any(Location.class), eq(8), anyDouble(), anyDouble(), anyDouble(), anyDouble());
    }

    @Test
    void restTransitionsPlaySounds() {
        engine.playRestStart(pet);
        engine.playWake(pet);

        verify(world).playSound(any(Location.class), eq(Sound.ENTITY_CAT_PURR), anyFloat(), anyFloat());
        verify(world).playSound(any(Location.class), eq(Sound.ENTITY_CAT_AMBIENT), anyFloat(), anyFloat());
    }

    @Test
    void disabledFeatureProducesNoWorldCalls() {
        configRef.set(snapshot(false));

        engine.playOwnerDamage(pet);
        engine.playLevelUp(pet);
        engine.playRestStart(pet);
        engine.playWake(pet);

        verifyNoInteractions(world);
    }

    @Test
    void invalidOrNullEntityIsIgnored() {
        when(pet.isValid()).thenReturn(false);
        engine.playOwnerDamage(pet);
        engine.playLevelUp(null);
        engine.playWake(null);
        engine.playRestStart(null);

        verifyNoInteractions(world);
    }

    @Test
    void missingConfigProducesNoWorldCalls() {
        configRef.set(null);
        engine.playOwnerDamage(pet);

        verifyNoInteractions(world);
    }

    private static PetDefinition definitionWithReaction(PetReactionType type, PetReactionDefinition reaction) {
        Map<PetReactionType, PetReactionDefinition> reactions = new LinkedHashMap<>();
        reactions.put(type, reaction);
        return new PetDefinition("test", "Test", List.of(), "WOLF", false, false, true,
                false, true, true, 100, true, List.of("<yellow>{pet_name}</yellow>"),
                null, null, null, null, reactions, null);
    }

    @Test
    void perPetReactionOverridesDefaultSoundAndCount() {
        Map<PetReactionType, PetReactionDefinition> reactions = new LinkedHashMap<>();
        reactions.put(PetReactionType.OWNER_DAMAGE, new PetReactionDefinition(true, "ENTITY_CAT_HISS", null, 7, 0.0));
        PetDefinition definition = new PetDefinition("test", "Test", List.of(), "WOLF", false, false, true,
                false, true, true, 100, true, List.of("<yellow>{pet_name}</yellow>"),
                null, null, null, null, reactions, null);

        engine.playOwnerDamage(pet, definition);

        verify(world).playSound(any(Location.class), eq(Sound.ENTITY_CAT_HISS), anyFloat(), anyFloat());
        verify(world).spawnParticle(eq(Particle.VILLAGER_ANGRY), any(Location.class), eq(7), anyDouble(), anyDouble(), anyDouble(), anyDouble());
    }

    @Test
    void disabledPerPetReactionProducesNoCalls() {
        PetDefinition definition = definitionWithReaction(PetReactionType.LEVEL_UP,
                new PetReactionDefinition(false, null, null, 0, 0.0));

        engine.playLevelUp(pet, definition);

        verifyNoInteractions(world);
    }

    @Test
    void definitionWithoutReactionFallsBackToDefaults() {
        engine.playOwnerDamage(pet, definitionWithReaction(PetReactionType.LEVEL_UP,
                new PetReactionDefinition(true, "ENTITY_CAT_HISS", null, 7, 0.0)));

        verify(world).playSound(any(Location.class), eq(Sound.ENTITY_WOLF_GROWL), anyFloat(), anyFloat());
    }

    @Test
    void perPetEmotePlaysSoundAndParticleBurst() {
        PetEmoteDefinition emote = new PetEmoteDefinition(true, "ENTITY_CAT_PURR", "HEART", 6, 10);
        PetDefinition definition = new PetDefinition("test", "Test", List.of(), "WOLF", false, false, true,
                false, true, true, 100, true, List.of("<yellow>{pet_name}</yellow>"),
                null, null, null, null, null,
                Map.of("purr", emote));

        engine.playEmote(pet, definition.emotes().get("purr"));

        verify(world).playSound(any(Location.class), eq(Sound.ENTITY_CAT_PURR), anyFloat(), anyFloat());
        verify(world).spawnParticle(eq(Particle.HEART), any(Location.class), eq(6), anyDouble(), anyDouble(), anyDouble(), anyDouble());
    }

    @Test
    void invalidPerPetSoundNameIsIgnoredButParticleStillPlays() {
        Map<PetReactionType, PetReactionDefinition> reactions = new LinkedHashMap<>();
        reactions.put(PetReactionType.OWNER_DAMAGE, new PetReactionDefinition(true, "NOT_A_REAL_SOUND", null, 3, 0.0));
        PetDefinition definition = new PetDefinition("test", "Test", List.of(), "WOLF", false, false, true,
                false, true, true, 100, true, List.of("<yellow>{pet_name}</yellow>"),
                null, null, null, null, reactions, null);

        engine.playOwnerDamage(pet, definition);

        verify(world, never()).playSound(any(Location.class), any(Sound.class), anyFloat(), anyFloat());
        verify(world).spawnParticle(any(Particle.class), any(Location.class), eq(3), anyDouble(), anyDouble(), anyDouble(), anyDouble());
    }

    @Test
    void disabledFeatureIgnoresPerPetOverrides() {
        configRef.set(snapshot(false));
        PetDefinition definition = definitionWithReaction(PetReactionType.OWNER_DAMAGE,
                new PetReactionDefinition(true, "ENTITY_CAT_HISS", "VILLAGER_ANGRY", 7, 0.9));

        engine.playOwnerDamage(pet, definition);
        engine.playEmote(pet, new PetEmoteDefinition(true, "ENTITY_CAT_PURR", "HEART", 6, 10));

        verifyNoInteractions(world);
    }
}

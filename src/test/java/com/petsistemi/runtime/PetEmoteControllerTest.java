package com.petsistemi.runtime;

import com.petsistemi.domain.PetEmoteDefinition;
import com.petsistemi.runtime.PetEmoteController.EmoteOutcome;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PetEmoteControllerTest {

    private final AtomicLong clock = new AtomicLong(10_000L);
    private final UUID ownerId = UUID.randomUUID();
    private PetEmoteController controller;
    private PetReactionEngine reactionEngine;
    private World world;
    private Entity pet;

    @BeforeEach
    void setUp() {
        reactionEngine = mock(PetReactionEngine.class);
        controller = new PetEmoteController(reactionEngine, clock::get);
        world = mock(World.class);
        pet = mock(Entity.class);
        when(pet.isValid()).thenReturn(true);
        when(pet.getWorld()).thenReturn(world);
    }

    private static Map<String, PetEmoteDefinition> emotes(PetEmoteDefinition purr, PetEmoteDefinition dance) {
        Map<String, PetEmoteDefinition> map = new LinkedHashMap<>();
        map.put("purr", purr);
        map.put("dance", dance);
        return map;
    }

    private static PetEmoteDefinition emote(String sound, int cooldownSeconds) {
        return new PetEmoteDefinition(true, sound, "VILLAGER_HAPPY", 5, cooldownSeconds);
    }

    @Test
    void playsEmoteAndForwardsToEngine() {
        Map<String, PetEmoteDefinition> emotes = emotes(emote("ENTITY_CAT_PURR", 10), null);

        EmoteOutcome outcome = controller.play(ownerId, pet, emotes, "purr");

        assertEquals(PetEmoteController.EmoteResult.PLAYED, outcome.result());
        verify(reactionEngine).playEmote(pet, emotes.get("purr"));
    }

    @Test
    void unknownEmoteIsRejectedWithoutEngineCalls() {
        Map<String, PetEmoteDefinition> emotes = emotes(emote("ENTITY_CAT_PURR", 10), null);

        EmoteOutcome outcome = controller.play(ownerId, pet, emotes, "dance");

        assertEquals(PetEmoteController.EmoteResult.UNKNOWN_EMOTE, outcome.result());
        verifyNoInteractions(reactionEngine);
    }

    @Test
    void cooldownBlocksSecondPlayWithinWindow() {
        Map<String, PetEmoteDefinition> emotes = emotes(emote("ENTITY_CAT_PURR", 10), null);
        assertEquals(PetEmoteController.EmoteResult.PLAYED, controller.play(ownerId, pet, emotes, "PURR").result());

        EmoteOutcome second = controller.play(ownerId, pet, emotes, "purr");

        assertEquals(PetEmoteController.EmoteResult.COOLDOWN, second.result());
        assertTrue(second.remainingSeconds() > 0 && second.remainingSeconds() <= 10);
        verify(reactionEngine, times(1)).playEmote(any(), any());
    }

    @Test
    void cooldownExpiresAfterWindow() {
        Map<String, PetEmoteDefinition> emotes = emotes(emote("ENTITY_CAT_PURR", 10), null);
        controller.play(ownerId, pet, emotes, "purr");

        clock.addAndGet(10_000L);
        EmoteOutcome outcome = controller.play(ownerId, pet, emotes, "purr");

        assertEquals(PetEmoteController.EmoteResult.PLAYED, outcome.result());
        verify(reactionEngine, times(2)).playEmote(any(), any());
    }

    @Test
    void zeroCooldownAllowsUnlimitedPlays() {
        Map<String, PetEmoteDefinition> emotes = emotes(emote("ENTITY_CAT_PURR", 0), null);

        assertEquals(PetEmoteController.EmoteResult.PLAYED, controller.play(ownerId, pet, emotes, "purr").result());
        assertEquals(PetEmoteController.EmoteResult.PLAYED, controller.play(ownerId, pet, emotes, "purr").result());
        verify(reactionEngine, times(2)).playEmote(any(), any());
    }

    @Test
    void disabledEmoteIsStillPlayableButEngineSkipsIt() {
        Map<String, PetEmoteDefinition> emotes = new LinkedHashMap<>();
        emotes.put("purr", new PetEmoteDefinition(false, "ENTITY_CAT_PURR", "VILLAGER_HAPPY", 5, 10));

        EmoteOutcome outcome = controller.play(ownerId, pet, emotes, "purr");

        assertEquals(PetEmoteController.EmoteResult.PLAYED, outcome.result());
        verify(reactionEngine).playEmote(pet, emotes.get("purr"));
    }

    @Test
    void invalidPetOrOwnerReturnsUnknown() {
        Map<String, PetEmoteDefinition> emotes = emotes(emote("ENTITY_CAT_PURR", 10), null);
        when(pet.isValid()).thenReturn(false);

        assertEquals(PetEmoteController.EmoteResult.UNKNOWN_EMOTE, controller.play(ownerId, pet, emotes, "purr").result());
        assertEquals(PetEmoteController.EmoteResult.UNKNOWN_EMOTE, controller.play(null, pet, emotes, "purr").result());
        assertEquals(PetEmoteController.EmoteResult.UNKNOWN_EMOTE, controller.play(ownerId, null, emotes, "purr").result());
        assertEquals(PetEmoteController.EmoteResult.UNKNOWN_EMOTE, controller.play(ownerId, pet, null, "purr").result());
        assertEquals(PetEmoteController.EmoteResult.UNKNOWN_EMOTE, controller.play(ownerId, pet, emotes, null).result());
        verifyNoInteractions(reactionEngine);
    }

    @Test
    void cleanupFreesCooldownState() {
        Map<String, PetEmoteDefinition> emotes = emotes(emote("ENTITY_CAT_PURR", 10), null);
        controller.play(ownerId, pet, emotes, "purr");

        controller.cleanup(ownerId);
        EmoteOutcome outcome = controller.play(ownerId, pet, emotes, "purr");

        assertEquals(PetEmoteController.EmoteResult.PLAYED, outcome.result());
        verify(reactionEngine, times(2)).playEmote(any(), any());
    }

    @Test
    void cooldownsArePerOwnerNotPerPet() {
        Map<String, PetEmoteDefinition> emotes = emotes(emote("ENTITY_CAT_PURR", 10), null);
        UUID otherOwner = UUID.randomUUID();

        assertEquals(PetEmoteController.EmoteResult.PLAYED, controller.play(ownerId, pet, emotes, "purr").result());
        assertEquals(PetEmoteController.EmoteResult.COOLDOWN, controller.play(ownerId, pet, emotes, "purr").result());
        assertEquals(PetEmoteController.EmoteResult.PLAYED, controller.play(otherOwner, pet, emotes, "purr").result());
        verify(reactionEngine, times(2)).playEmote(any(), any());
    }
}

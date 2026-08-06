package com.petsistemi.runtime;

import com.petsistemi.domain.PetEmoteDefinition;
import org.bukkit.entity.Entity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * Plays per-pet emotes with per-owner per-emote cooldowns. Runs on the main
 * thread (commands/events); {@code play} returns an outcome for messaging.
 */
public class PetEmoteController {

    public enum EmoteResult { PLAYED, UNKNOWN_EMOTE, COOLDOWN }

    private final PetReactionEngine reactionEngine;
    private final LongSupplier clock;

    private final Map<UUID, Map<String, Long>> cooldowns = new HashMap<>();

    public PetEmoteController(PetReactionEngine reactionEngine) {
        this(reactionEngine, System::currentTimeMillis);
    }

    /** Test-friendly constructor with an injectable clock. */
    public PetEmoteController(PetReactionEngine reactionEngine, LongSupplier clock) {
        this.reactionEngine = reactionEngine;
        this.clock = clock;
    }

    /**
     * Plays the given emote on the pet if it exists and its cooldown has expired.
     * Cooldowns are tracked per owner, so two pets of the same owner share one.
     * Returns the remaining cooldown seconds via {@link EmoteOutcome} when blocked.
     */
    public EmoteOutcome play(UUID ownerId, Entity petEntity, Map<String, PetEmoteDefinition> emotes, String emoteName) {
        if (emotes == null || emoteName == null) {
            return EmoteOutcome.unknown();
        }
        PetEmoteDefinition emote = emotes.get(emoteName.toLowerCase());
        if (emote == null) {
            return EmoteOutcome.unknown();
        }
        if (ownerId == null || petEntity == null || !petEntity.isValid()) {
            return EmoteOutcome.unknown();
        }

        long now = clock.getAsLong();
        long cooldownMs = Math.max(0L, emote.cooldownSeconds()) * 1000L;
        if (cooldownMs > 0L) {
            Long last = cooldown(ownerId).get(emoteName.toLowerCase());
            if (last != null) {
                long remainingMs = last + cooldownMs - now;
                if (remainingMs > 0L) {
                    return EmoteOutcome.cooldown((remainingMs + 999L) / 1000L);
                }
            }
        }

        if (reactionEngine != null) {
            reactionEngine.playEmote(petEntity, emote);
        }
        if (cooldownMs > 0L) {
            cooldown(ownerId).put(emoteName.toLowerCase(), now);
        }
        return EmoteOutcome.played();
    }

    /** Frees cooldown state for a despawned pet/owner. */
    public void cleanup(UUID ownerId) {
        if (ownerId != null) {
            cooldowns.remove(ownerId);
        }
    }

    private Map<String, Long> cooldown(UUID ownerId) {
        return cooldowns.computeIfAbsent(ownerId, k -> new HashMap<>());
    }

    public record EmoteOutcome(EmoteResult result, String emoteName, long remainingSeconds) {
        static EmoteOutcome played() { return new EmoteOutcome(EmoteResult.PLAYED, null, 0); }
        static EmoteOutcome unknown() { return new EmoteOutcome(EmoteResult.UNKNOWN_EMOTE, null, 0); }
        static EmoteOutcome cooldown(long remainingSeconds) { return new EmoteOutcome(EmoteResult.COOLDOWN, null, remainingSeconds); }
    }
}

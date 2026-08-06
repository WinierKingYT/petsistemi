package com.petsistemi.runtime;

import com.petsistemi.config.RuntimeConfigurationSnapshot;
import com.petsistemi.domain.PetDefinition;
import com.petsistemi.domain.PetEmoteDefinition;
import com.petsistemi.domain.PetReactionDefinition;
import com.petsistemi.domain.PetReactionType;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Plays pet reactions (sounds + particles at the pet's location) for owner
 * damage, level-ups, rest transitions and emotes. Gated by
 * {@code features.reactions.enabled}. Per-pet {@code reactions:}/{@code emotes:}
 * definitions override the global defaults; {@code null} fields keep the default.
 * All Bukkit calls are wrapped defensively for version compatibility.
 */
public class PetReactionEngine {

    private static final String DEFAULT_OWNER_DAMAGE_SOUND = "ENTITY_WOLF_GROWL";
    private static final String DEFAULT_OWNER_DAMAGE_PARTICLE = "VILLAGER_ANGRY";
    private static final String DEFAULT_REST_START_SOUND = "ENTITY_CAT_PURR";
    private static final String DEFAULT_REST_END_SOUND = "ENTITY_CAT_AMBIENT";
    private static final String DEFAULT_LEVEL_UP_SOUND = "ENTITY_CAT_PURR";
    private static final String DEFAULT_LEVEL_UP_PARTICLE = "VILLAGER_HAPPY";

    private final AtomicReference<RuntimeConfigurationSnapshot> configSnapshot;

    public PetReactionEngine(AtomicReference<RuntimeConfigurationSnapshot> configSnapshot) {
        this.configSnapshot = configSnapshot;
    }

    private boolean enabled() {
        return configSnapshot != null
                && configSnapshot.get() != null
                && configSnapshot.get().configuration() != null
                && configSnapshot.get().configuration().features().reactionsEnabled();
    }

    public void playRestStart(Entity petEntity) {
        playRestStart(petEntity, null);
    }

    public void playRestStart(Entity petEntity, PetDefinition definition) {
        playReaction(petEntity, PetReactionType.REST_START, definition,
                DEFAULT_REST_START_SOUND, null, 0, 0.8f);
    }

    public void playWake(Entity petEntity) {
        playWake(petEntity, null);
    }

    public void playWake(Entity petEntity, PetDefinition definition) {
        playReaction(petEntity, PetReactionType.REST_END, definition,
                DEFAULT_REST_END_SOUND, null, 0, 0.7f);
    }

    public void playOwnerDamage(Entity petEntity) {
        playOwnerDamage(petEntity, null);
    }

    public void playOwnerDamage(Entity petEntity, PetDefinition definition) {
        playReaction(petEntity, PetReactionType.OWNER_DAMAGE, definition,
                DEFAULT_OWNER_DAMAGE_SOUND, DEFAULT_OWNER_DAMAGE_PARTICLE, 3, 0.9f);
    }

    public void playLevelUp(Entity petEntity) {
        playLevelUp(petEntity, null);
    }

    public void playLevelUp(Entity petEntity, PetDefinition definition) {
        playReaction(petEntity, PetReactionType.LEVEL_UP, definition,
                DEFAULT_LEVEL_UP_SOUND, DEFAULT_LEVEL_UP_PARTICLE, 8, 0.9f);
    }

    /** Plays a per-pet emote (sound + particle burst). */
    public void playEmote(Entity petEntity, PetEmoteDefinition emote) {
        if (!enabled() || petEntity == null || !petEntity.isValid() || emote == null || !emote.enabled()) {
            return;
        }
        sound(petEntity, parseSound(emote.sound()), emote.sound(), 0.8f);
        int count = emote.particleCount() > 0 ? emote.particleCount() : 5;
        particle(petEntity, emote.particle(), count, 0.4, 0.4, 0.4, 0.1);
    }

    private void playReaction(Entity petEntity, PetReactionType type, PetDefinition definition,
                              String defaultSound, String defaultParticle, int defaultCount, float defaultVolume) {
        if (!enabled() || petEntity == null || !petEntity.isValid()) {
            return;
        }
        PetReactionDefinition reaction = reactionFor(definition, type);
        if (reaction != null && !reaction.enabled()) {
            return;
        }
        String soundName = reaction != null && reaction.sound() != null ? reaction.sound() : defaultSound;
        String particleName = reaction != null && reaction.particle() != null ? reaction.particle() : defaultParticle;
        int count = reaction != null && reaction.particleCount() > 0 ? reaction.particleCount() : defaultCount;
        float volume = reaction != null && reaction.volume() > 0.0 ? (float) reaction.volume() : defaultVolume;

        sound(petEntity, parseSound(soundName), soundName, volume);
        if (particleName != null) {
            particle(petEntity, particleName, count, 0.4, 0.4, 0.4, 0.1);
        }
    }

    private static PetReactionDefinition reactionFor(PetDefinition definition, PetReactionType type) {
        if (definition == null) return null;
        Map<PetReactionType, PetReactionDefinition> reactions = definition.reactions();
        return reactions != null ? reactions.get(type) : null;
    }

    private static Sound parseSound(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return Sound.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private void sound(Entity petEntity, Sound sound, String rawName, float volume) {
        if (sound == null) return;
        try {
            petEntity.getWorld().playSound(petEntity.getLocation(), sound, volume, 1.2f);
        } catch (Throwable ignored) {}
    }

    private void particle(Entity petEntity, String particleName, int count, double dx, double dy, double dz, double speed) {
        if (particleName == null || particleName.isBlank()) return;
        try {
            Particle particle = Particle.valueOf(particleName);
            World world = petEntity.getWorld();
            if (world != null) {
                world.spawnParticle(particle, petEntity.getLocation().add(0.0, 0.8, 0.0), count, dx, dy, dz, speed);
            }
        } catch (Throwable ignored) {}
    }
}

package com.petsistemi.runtime.behavior;

import com.petsistemi.domain.PetEmoteDefinition;
import com.petsistemi.domain.PetBuffDefinition;
import com.petsistemi.domain.PetReactionDefinition;
import com.petsistemi.domain.PetReactionType;
import com.petsistemi.domain.behavior.BehaviorActionDefinition;
import com.petsistemi.domain.behavior.BehaviorConditionDefinition;
import com.petsistemi.domain.behavior.PetBehaviorDefinition;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Converts the published reactions:/emotes: schemas into behavior pipelines. */
public final class LegacyBehaviorAdapter {
    private LegacyBehaviorAdapter() {}

    public static PetBehaviorDefinition reaction(PetReactionType type, PetReactionDefinition override,
                                                  String defaultSound, String defaultParticle,
                                                  int defaultCount, double defaultVolume) {
        Map<String, Object> parameters = effectParameters(
                override != null && override.sound() != null ? override.sound() : defaultSound,
                override != null && override.particle() != null ? override.particle() : defaultParticle,
                override != null && override.particleCount() > 0 ? override.particleCount() : defaultCount,
                override != null && override.volume() > 0.0 ? override.volume() : defaultVolume);
        return new PetBehaviorDefinition(BuiltInBehaviorKeys.reaction(type),
                override == null || override.enabled(), List.of(),
                List.of(new BehaviorActionDefinition(BuiltInBehaviorKeys.PLAY_EFFECT, parameters)));
    }

    public static PetBehaviorDefinition emote(PetEmoteDefinition emote) {
        Map<String, Object> parameters = effectParameters(emote != null ? emote.sound() : null,
                emote != null ? emote.particle() : null,
                emote != null && emote.particleCount() > 0 ? emote.particleCount() : 5, 0.8);
        return new PetBehaviorDefinition(BuiltInBehaviorKeys.EMOTE, emote != null && emote.enabled(),
                List.of(), List.of(new BehaviorActionDefinition(BuiltInBehaviorKeys.PLAY_EFFECT, parameters)));
    }

    public static PetBehaviorDefinition buff(PetBuffDefinition buff) {
        Map<String, Object> condition = Map.of("level", buff.minLevel());
        Map<String, Object> action = new LinkedHashMap<>();
        action.put("effect", buff.effectType().getName());
        action.put("amplifier", buff.amplifier());
        action.put("duration-ticks", buff.durationTicks());
        return new PetBehaviorDefinition(BuiltInBehaviorKeys.TICK, true,
                List.of(new BehaviorConditionDefinition(BuiltInBehaviorKeys.MIN_LEVEL, condition)),
                List.of(new BehaviorActionDefinition(BuiltInBehaviorKeys.APPLY_POTION_EFFECT, action)));
    }

    private static Map<String, Object> effectParameters(String sound, String particle, int count, double volume) {
        Map<String, Object> parameters = new LinkedHashMap<>();
        if (sound != null) parameters.put("sound", sound);
        if (particle != null) parameters.put("particle", particle);
        parameters.put("particle-count", count);
        parameters.put("volume", volume);
        return parameters;
    }
}

package com.petsistemi.domain.visual;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Keyframe channels keyed by normalized bone id. */
public record PetDisplayAnimationDefinition(int durationTicks, boolean loop,
                                            Map<String, List<PetDisplayKeyframeDefinition>> channels) {
    public PetDisplayAnimationDefinition {
        if (durationTicks <= 0 || durationTicks > 12000) {
            throw new IllegalArgumentException("Display model animation duration-ticks 1-12000 aralığında olmalıdır.");
        }
        Map<String, List<PetDisplayKeyframeDefinition>> normalized = new LinkedHashMap<>();
        if (channels != null) {
            channels.forEach((rawBone, rawFrames) -> {
                String bone = rawBone == null ? "" : rawBone.trim().toLowerCase(java.util.Locale.ROOT);
                if (!bone.matches("[a-z0-9][a-z0-9._-]{0,63}")) {
                    throw new IllegalArgumentException("Geçersiz display animation bone id: " + rawBone);
                }
                List<PetDisplayKeyframeDefinition> frames = rawFrames == null ? List.of()
                        : rawFrames.stream().sorted(java.util.Comparator.comparingInt(PetDisplayKeyframeDefinition::tick)).toList();
                if (frames.isEmpty()) throw new IllegalArgumentException("Display animation channel boş: " + bone);
                if (frames.get(frames.size() - 1).tick() > durationTicks) {
                    throw new IllegalArgumentException(bone + " keyframe tick duration-ticks değerini aşıyor.");
                }
                normalized.put(bone, frames);
            });
        }
        if (normalized.isEmpty()) throw new IllegalArgumentException("Display animation en az bir bone channel içermelidir.");
        channels = java.util.Collections.unmodifiableMap(normalized);
    }
}

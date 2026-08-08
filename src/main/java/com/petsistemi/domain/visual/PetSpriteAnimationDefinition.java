package com.petsistemi.domain.visual;

import java.util.List;

/** Ordered resource-pack custom-model-data frames for one runtime animation state. */
public record PetSpriteAnimationDefinition(int frameTicks, boolean loop, List<Integer> frames) {
    public PetSpriteAnimationDefinition {
        if (frameTicks <= 0 || frameTicks > 12000) {
            throw new IllegalArgumentException("Sprite frame-ticks 1-12000 aralığında olmalıdır.");
        }
        frames = frames == null ? List.of() : List.copyOf(frames);
        if (frames.isEmpty()) {
            throw new IllegalArgumentException("Sprite animation en az bir frame içermelidir.");
        }
        if (frames.size() > 256) {
            throw new IllegalArgumentException("Sprite animation en fazla 256 frame içerebilir.");
        }
        if (frames.stream().anyMatch(frame -> frame == null || frame < 0)) {
            throw new IllegalArgumentException("Sprite frame custom-model-data değerleri negatif olamaz.");
        }
    }
}

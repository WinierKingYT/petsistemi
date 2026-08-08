package com.petsistemi.runtime.animation;

import com.petsistemi.domain.animation.PetAnimationClipDefinition;
import com.petsistemi.domain.animation.PetAnimationState;

/** Complete provider-facing transition payload, including blend and priority metadata. */
public record PetAnimationTransition(
        PetAnimationState previousState,
        PetAnimationClipDefinition previousClip,
        PetAnimationState state,
        PetAnimationClipDefinition clip
) {}

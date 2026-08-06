package com.petsistemi.domain;

/**
 * One entry of the per-pet {@code transforms:} section. When all {@code when}
 * conditions match at runtime, the {@code apply} visual overrides are rendered
 * instead of the base representation (same {@link PetInstance} is kept).
 */
public record PetTransformDefinition(PetTransformCondition condition, PetVisualOverride apply) {
}

package com.petsistemi.api.model;

import org.bukkit.entity.Entity;

import java.util.Objects;

/** Runtime handle returned by an optional external model provider. */
public record PetModelHandle(Entity entity, Object providerHandle, String modelId) {
    public PetModelHandle {
        Objects.requireNonNull(entity, "model provider entity null olamaz.");
        Objects.requireNonNull(modelId, "modelId null olamaz.");
    }
}

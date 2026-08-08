package com.petsistemi.runtime.visual;

import com.petsistemi.domain.visual.PetVisualTransform;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;

import java.util.Optional;

/** Runtime counterpart of a visual node; entity is empty for future virtual components. */
public record PetVisualComponent(String id, String parentId, NamespacedKey representationKey,
                                 PetVisualTransform localTransform, Entity entity) {
    public PetVisualComponent {
        if (id == null || !id.matches("[a-z0-9][a-z0-9._-]{0,63}")) {
            throw new IllegalArgumentException("Geçersiz runtime visual component id: " + id);
        }
        if (representationKey == null) throw new IllegalArgumentException("Visual component representation key eksik: " + id);
        localTransform = localTransform != null ? localTransform : PetVisualTransform.IDENTITY;
    }

    public Optional<Entity> serverEntity() {
        return Optional.ofNullable(entity);
    }
}

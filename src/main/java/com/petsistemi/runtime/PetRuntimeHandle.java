package com.petsistemi.runtime;

import com.petsistemi.domain.RuntimeRepresentationType;
import com.petsistemi.runtime.visual.PetVisualHandle;
import org.bukkit.entity.Entity;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/**
 * Generic runtime handle for a spawned pet. Runtime code should never depend on
 * {@link org.bukkit.entity.LivingEntity} directly — a pet may be a mob, a display
 * entity, a particle cloud or a multi-entity formation.
 */
public interface PetRuntimeHandle {

    UUID ownerId();

    UUID petId();

    RuntimeRepresentationType representationType();

    Optional<Entity> primaryEntity();

    Collection<Entity> entities();

    default Optional<PetVisualHandle> visualHandle() { return Optional.empty(); }

    boolean isValid();

    void remove();
}

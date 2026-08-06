package com.petsistemi.runtime;

import com.petsistemi.domain.PetDefinition;
import com.petsistemi.domain.PetInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Spawns and manages the visual representation of a pet in the world.
 * Implementations: {@code EntityPetRepresentation}, {@code ItemDisplayPetRepresentation},
 * {@code BlockDisplayPetRepresentation}, {@code TextDisplayPetRepresentation},
 * {@code ParticlePetRepresentation}, {@code MultiEntityPetRepresentation}, ...
 */
public interface PetRepresentationController {

    Entity spawn(PetInstance pet, PetDefinition definition, Player owner);

    /** Called on each runtime tick to animate visuals that need per-tick updates (e.g. particles). */
    default void tickVisual(Entity primaryEntity, PetInstance pet, PetDefinition definition, Player owner) {
        // no-op
    }

    /**
     * Applies the idle/sleep visual state (e.g. sitting for Sittable mobs, a
     * scale-down for display pets). Called on rest transitions only.
     */
    default void applyRestState(Entity primaryEntity, PetInstance pet, PetDefinition definition, boolean resting) {
        // no-op
    }

    /**
     * Optional secondary entities (e.g. MULTI_ENTITY swarms). Called right after
     * {@link #spawn}; the returned entities are tracked on the runtime handle and
     * cleaned up together with the primary.
     */
    default List<Entity> spawnChildren(Entity primaryEntity, PetInstance pet, PetDefinition definition, Player owner) {
        return List.of();
    }

    void updateVisual(Entity primaryEntity, PetInstance pet, PetDefinition definition);

    void remove(Entity primaryEntity);

    boolean isValid(Entity primaryEntity);
}

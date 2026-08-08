package com.petsistemi.runtime;

import com.petsistemi.domain.PetDefinition;
import com.petsistemi.domain.PetInstance;
import com.petsistemi.domain.animation.PetAnimationState;
import com.petsistemi.domain.visual.PetVisualTransform;
import com.petsistemi.runtime.animation.PetAnimationTransition;
import com.petsistemi.runtime.visual.PetRenderBackend;
import com.petsistemi.runtime.visual.PetVisualComponent;
import com.petsistemi.runtime.visual.PetVisualHandle;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Objects;

/**
 * Spawns and manages the visual representation of a pet in the world.
 * Implementations: {@code EntityPetRepresentation}, {@code ItemDisplayPetRepresentation},
 * {@code BlockDisplayPetRepresentation}, {@code TextDisplayPetRepresentation},
 * {@code ParticlePetRepresentation}, {@code MultiEntityPetRepresentation}, ...
 */
public interface PetRepresentationController {

    Entity spawn(PetInstance pet, PetDefinition definition, Player owner);

    /**
     * Graph-aware spawn seam. Existing controllers are adapted automatically to a root
     * component and stable child ids; COMPOSITE controllers can override this directly.
     */
    default PetVisualHandle spawnVisual(PetInstance pet, PetDefinition definition, Player owner) {
        Entity primary = Objects.requireNonNull(spawn(pet, definition, owner),
                "Representation controller spawn null entity döndü");
        org.bukkit.NamespacedKey key = definition.representationOrEntity().key();
        PetVisualHandle.Builder visual = PetVisualHandle.builder(PetVisualHandle.ROOT_COMPONENT, PetRenderBackend.SERVER)
                .component(new PetVisualComponent(PetVisualHandle.ROOT_COMPONENT, null, key,
                        PetVisualTransform.IDENTITY, primary));
        List<Entity> children = spawnChildren(primary, pet, definition, owner);
        if (children == null) children = List.of();
        for (int i = 0; i < children.size(); i++) {
            visual.component(new PetVisualComponent("child-" + (i + 1), PetVisualHandle.ROOT_COMPONENT,
                    key, PetVisualTransform.IDENTITY, children.get(i)));
        }
        return visual.build();
    }

    /** Called on each runtime tick to animate visuals that need per-tick updates (e.g. particles). */
    default void tickVisual(Entity primaryEntity, PetInstance pet, PetDefinition definition, Player owner) {
        // no-op
    }

    default void tickVisualHandle(PetVisualHandle visual, PetInstance pet, PetDefinition definition, Player owner) {
        if (visual != null) visual.primaryEntity().ifPresent(entity -> tickVisual(entity, pet, definition, owner));
    }

    /**
     * Applies the idle/sleep visual state (e.g. sitting for Sittable mobs, a
     * scale-down for display pets). Called on rest transitions only.
     */
    default void applyRestState(Entity primaryEntity, PetInstance pet, PetDefinition definition, boolean resting) {
        // no-op
    }

    default void applyRestStateHandle(PetVisualHandle visual, PetInstance pet, PetDefinition definition, boolean resting) {
        if (visual != null) visual.primaryEntity().ifPresent(entity -> applyRestState(entity, pet, definition, resting));
    }

    /**
     * Shared animation-provider seam. Vanilla and display controllers get a meaningful
     * default adapter; model providers can override this method and consume named clip,
     * priority and blend metadata directly.
     */
    default void applyAnimation(Entity primaryEntity, PetInstance pet, PetDefinition definition,
                                PetAnimationTransition transition) {
        if (transition == null) return;
        boolean wasResting = transition.previousState() == PetAnimationState.SLEEPING;
        boolean resting = transition.state() == PetAnimationState.SLEEPING;
        if (wasResting != resting) {
            applyRestState(primaryEntity, pet, definition, resting);
        }
    }

    default void applyAnimationHandle(PetVisualHandle visual, PetInstance pet, PetDefinition definition,
                                      PetAnimationTransition transition) {
        if (visual != null) visual.primaryEntity().ifPresent(entity -> applyAnimation(entity, pet, definition, transition));
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

    default void updateVisualHandle(PetVisualHandle visual, PetInstance pet, PetDefinition definition) {
        if (visual != null) visual.primaryEntity().ifPresent(entity -> updateVisual(entity, pet, definition));
    }

    void remove(Entity primaryEntity);

    /** Removes provider-owned root state first, then every remaining server component. */
    default void removeVisualHandle(PetVisualHandle visual) {
        if (visual == null) return;
        Entity primary = visual.primaryEntity().orElse(null);
        if (primary != null) remove(primary);
        for (Entity entity : visual.secondaryEntities()) {
            if (entity != null && entity.isValid()) entity.remove();
        }
    }

    boolean isValid(Entity primaryEntity);

    default boolean isVisualHandleValid(PetVisualHandle visual) {
        if (visual == null) return false;
        return visual.primaryEntity().map(this::isValid).orElseGet(visual::isValid);
    }
}

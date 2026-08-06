package com.petsistemi.runtime;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

/**
 * Controls how an active pet moves relative to its owner.
 * Implementations: {@code GroundFollowMovement}, {@code FlyingFollowMovement}, {@code OrbitMovement}, ...
 */
public interface PetMovementController {

    void initialize(ActivePet pet, Entity entity, Player owner);

    void tick(ActivePet pet, Entity entity, Player owner);

    void remove(ActivePet pet, Entity entity);
}

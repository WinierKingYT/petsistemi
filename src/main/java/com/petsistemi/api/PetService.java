package com.petsistemi.api;

import com.petsistemi.api.result.*;
import org.bukkit.entity.Player;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface PetService {

    Optional<PetSnapshot> findPet(UUID petId);

    Collection<PetSnapshot> getOwnedPets(UUID ownerId);

    /**
     * Gets the player's saved active pet selection preference from database.
     */
    Optional<PetSnapshot> getSelectedPet(UUID ownerId);

    /**
     * Gets the pet snapshot ONLY if there is currently a spawned physical entity in the world.
     */
    Optional<PetSnapshot> getSpawnedPet(UUID ownerId);

    /**
     * @deprecated Use {@link #getSpawnedPet(UUID)} or {@link #getSelectedPet(UUID)} instead for clear state intention.
     */
    @Deprecated
    Optional<PetSnapshot> getActivePet(UUID ownerId);

    PetGiveResult givePet(UUID ownerId, String definitionId);

    PetSummonResult summon(Player owner, UUID petId);

    PetDismissResult dismiss(Player owner);

    PetRenameResult rename(Player owner, UUID petId, String newName);

    PetRenameResult rename(UUID ownerId, UUID petId, String newName);
}

package com.petsistemi.api;

import com.petsistemi.api.result.*;
import org.bukkit.entity.Player;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface PetService {

    Optional<PetSnapshot> findPet(UUID petId);

    Collection<PetSnapshot> getOwnedPets(UUID ownerId);

    Optional<PetSnapshot> getActivePet(UUID ownerId);

    PetGiveResult givePet(UUID ownerId, String definitionId);

    PetSummonResult summon(Player owner, UUID petId);

    PetDismissResult dismiss(Player owner);

    PetRenameResult rename(Player owner, UUID petId, String newName);
}

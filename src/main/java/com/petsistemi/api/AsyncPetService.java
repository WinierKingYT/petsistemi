package com.petsistemi.api;

import com.petsistemi.api.result.PetDisableResult;
import com.petsistemi.api.result.PetGiveResult;
import com.petsistemi.api.result.PetRemoveResult;
import com.petsistemi.api.result.PetRenameResult;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface AsyncPetService {
    CompletableFuture<Optional<PetSnapshot>> findPetAsync(UUID petId);
    CompletableFuture<Collection<PetSnapshot>> getOwnedPetsAsync(UUID ownerId);
    CompletableFuture<Optional<PetSnapshot>> getSelectedPetAsync(UUID ownerId);
    CompletableFuture<PetGiveResult> givePetAsync(UUID ownerId, String definitionId);
    CompletableFuture<PetRenameResult> renameAsync(UUID ownerId, UUID petId, String newName);
    CompletableFuture<PetDisableResult> disablePetAsync(UUID petId);
    CompletableFuture<PetDisableResult> enablePetAsync(UUID petId);
    CompletableFuture<PetRemoveResult> removePetAsync(UUID petId);
}

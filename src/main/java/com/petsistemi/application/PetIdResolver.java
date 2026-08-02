package com.petsistemi.application;

import com.petsistemi.domain.PetInstance;
import com.petsistemi.persistence.PetRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class PetIdResolver {

    public sealed interface ResolutionResult {
        record Found(UUID petId) implements ResolutionResult {}
        record NotFound() implements ResolutionResult {}
        record Ambiguous(List<UUID> matches) implements ResolutionResult {}
        record InvalidFormat() implements ResolutionResult {}
    }

    public static ResolutionResult resolve(String input, UUID ownerId, PetRepository repository) {
        if (input == null || input.trim().isEmpty()) {
            return new ResolutionResult.InvalidFormat();
        }

        String clean = input.trim();

        try {
            UUID exactUuid = UUID.fromString(clean);
            Optional<PetInstance> pet = repository.findById(exactUuid);
            if (pet.isPresent()) {
                return new ResolutionResult.Found(exactUuid);
            }
            return new ResolutionResult.NotFound();
        } catch (IllegalArgumentException ignored) {}

        List<PetInstance> ownedPets = repository.findByOwner(ownerId);
        List<UUID> matches = new ArrayList<>();
        String lowerInput = clean.toLowerCase();

        for (PetInstance pet : ownedPets) {
            String shortId = pet.petId().toString().substring(0, 8).toLowerCase();
            if (shortId.startsWith(lowerInput) || pet.petId().toString().toLowerCase().startsWith(lowerInput)) {
                matches.add(pet.petId());
            }
        }

        if (matches.size() == 1) {
            return new ResolutionResult.Found(matches.get(0));
        } else if (matches.size() > 1) {
            return new ResolutionResult.Ambiguous(matches);
        }

        return new ResolutionResult.NotFound();
    }
}

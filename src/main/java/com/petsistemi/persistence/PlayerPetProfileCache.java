package com.petsistemi.persistence;

import com.petsistemi.api.PetSnapshot;
import com.petsistemi.domain.PetInstance;
import com.petsistemi.domain.PetSelection;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerPetProfileCache {

    private final PetRepository petRepository;
    private final PetSelectionRepository selectionRepository;
    private final Map<UUID, PlayerPetProfile> profiles = new ConcurrentHashMap<>();

    public PlayerPetProfileCache(PetRepository petRepository, PetSelectionRepository selectionRepository) {
        this.petRepository = petRepository;
        this.selectionRepository = selectionRepository;
    }

    public PlayerPetProfile loadProfile(UUID ownerId) {
        List<PetInstance> pets = petRepository.findByOwner(ownerId);
        Optional<PetSelection> selection = selectionRepository.findByOwner(ownerId);
        UUID selectedId = selection.map(PetSelection::petId).orElse(null);

        Map<UUID, PetSnapshot> petMap = new HashMap<>();
        for (PetInstance pet : pets) {
            boolean isSelected = selectedId != null && selectedId.equals(pet.petId());
            petMap.put(pet.petId(), new PetSnapshot(
                    pet.petId(),
                    pet.ownerId(),
                    pet.definitionId(),
                    pet.customName(),
                    pet.level(),
                    pet.experience(),
                    pet.availabilityState(),
                    isSelected,
                    false
            ));
        }

        PlayerPetProfile profile = new PlayerPetProfile(ownerId, Collections.unmodifiableMap(petMap), selectedId, System.currentTimeMillis(), 1L);
        profiles.put(ownerId, profile);
        return profile;
    }

    public Optional<PlayerPetProfile> getProfile(UUID ownerId) {
        return Optional.ofNullable(profiles.get(ownerId));
    }

    public void evict(UUID ownerId) {
        profiles.remove(ownerId);
    }

    public void invalidate(UUID ownerId) {
        evict(ownerId);
    }

    public int size() {
        return profiles.size();
    }

    public void clearAll() {
        profiles.clear();
    }
}

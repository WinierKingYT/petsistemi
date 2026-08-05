package com.petsistemi.persistence;

import com.petsistemi.api.PetSnapshot;
import com.petsistemi.domain.PetAvailabilityState;
import com.petsistemi.domain.PetInstance;
import com.petsistemi.domain.PetSelection;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class PlayerPetProfileCache {

    private final PetRepository petRepository;
    private final PetSelectionRepository selectionRepository;
    private final Map<UUID, PlayerPetProfile> profiles = new ConcurrentHashMap<>();
    private final Map<UUID, AtomicLong> ownerGenerations = new ConcurrentHashMap<>();
    private final Map<UUID, CompletableFuture<PlayerPetProfile>> inFlightLoads = new ConcurrentHashMap<>();

    public PlayerPetProfileCache(PetRepository petRepository, PetSelectionRepository selectionRepository) {
        this.petRepository = petRepository;
        this.selectionRepository = selectionRepository;
    }

    public long beginLoad(UUID ownerId) {
        return ownerGenerations.computeIfAbsent(ownerId, k -> new AtomicLong(0L)).incrementAndGet();
    }

    public boolean completeLoad(UUID ownerId, long generation, PlayerPetProfile profile) {
        AtomicLong currentGen = ownerGenerations.get(ownerId);
        if (currentGen != null && currentGen.get() == generation && profile != null) {
            profiles.put(ownerId, profile);
            return true;
        }
        return false;
    }

    public PlayerPetProfile loadProfile(UUID ownerId) {
        long gen = beginLoad(ownerId);
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
        completeLoad(ownerId, gen, profile);
        return profile;
    }

    public CompletableFuture<PlayerPetProfile> loadProfileAsync(DatabaseExecutor dbExecutor, UUID ownerId) {
        PlayerPetProfile existing = profiles.get(ownerId);
        if (existing != null) {
            return CompletableFuture.completedFuture(existing);
        }

        return inFlightLoads.computeIfAbsent(ownerId, k -> {
            if (dbExecutor == null) {
                try {
                    PlayerPetProfile loaded = loadProfile(ownerId);
                    return CompletableFuture.completedFuture(loaded);
                } catch (Exception e) {
                    CompletableFuture<PlayerPetProfile> failed = new CompletableFuture<>();
                    failed.completeExceptionally(e);
                    return failed;
                } finally {
                    inFlightLoads.remove(ownerId);
                }
            } else {
                long gen = beginLoad(ownerId);
                return dbExecutor.submit(() -> {
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
                    return new PlayerPetProfile(ownerId, Collections.unmodifiableMap(petMap), selectedId, System.currentTimeMillis(), 1L);
                }).thenApply(loaded -> {
                    completeLoad(ownerId, gen, loaded);
                    return loaded;
                }).whenComplete((res, ex) -> inFlightLoads.remove(ownerId));
            }
        });
    }

    public Optional<PlayerPetProfile> getProfile(UUID ownerId) {
        return Optional.ofNullable(profiles.get(ownerId));
    }

    public void putLoadedProfile(PlayerPetProfile profile) {
        if (profile != null) {
            beginLoad(profile.ownerId());
            profiles.put(profile.ownerId(), profile);
        }
    }

    public void updatePet(UUID ownerId, PetSnapshot pet) {
        if (ownerId == null || pet == null) return;
        profiles.computeIfPresent(ownerId, (id, current) -> {
            Map<UUID, PetSnapshot> newMap = new HashMap<>(current.pets());
            newMap.put(pet.petId(), pet);
            return new PlayerPetProfile(ownerId, Collections.unmodifiableMap(newMap), current.selectedPetId(), System.currentTimeMillis(), current.version() + 1);
        });
    }

    public void removePet(UUID ownerId, UUID petId) {
        if (ownerId == null || petId == null) return;
        profiles.computeIfPresent(ownerId, (id, current) -> {
            Map<UUID, PetSnapshot> newMap = new HashMap<>(current.pets());
            newMap.remove(petId);
            UUID newSelected = Objects.equals(current.selectedPetId(), petId) ? null : current.selectedPetId();
            return new PlayerPetProfile(ownerId, Collections.unmodifiableMap(newMap), newSelected, System.currentTimeMillis(), current.version() + 1);
        });
    }

    public void updateSelection(UUID ownerId, UUID selectedPetId) {
        if (ownerId == null) return;
        profiles.computeIfPresent(ownerId, (id, current) -> {
            Map<UUID, PetSnapshot> newMap = new HashMap<>();
            for (PetSnapshot p : current.pets().values()) {
                boolean isSel = selectedPetId != null && selectedPetId.equals(p.petId());
                newMap.put(p.petId(), new PetSnapshot(p.petId(), p.ownerId(), p.definitionId(), p.customName(), p.level(), p.experience(), p.availabilityState(), isSel, p.spawned()));
            }
            return new PlayerPetProfile(ownerId, Collections.unmodifiableMap(newMap), selectedPetId, System.currentTimeMillis(), current.version() + 1);
        });
    }

    public void clearSelection(UUID ownerId) {
        updateSelection(ownerId, null);
    }

    public void updateExperience(UUID ownerId, UUID petId, int level, long experience) {
        if (ownerId == null || petId == null) return;
        profiles.computeIfPresent(ownerId, (id, current) -> {
            PetSnapshot old = current.pets().get(petId);
            if (old == null) return current;
            PetSnapshot updated = new PetSnapshot(old.petId(), old.ownerId(), old.definitionId(), old.customName(), level, experience, old.availabilityState(), old.selected(), old.spawned());
            Map<UUID, PetSnapshot> newMap = new HashMap<>(current.pets());
            newMap.put(petId, updated);
            return new PlayerPetProfile(ownerId, Collections.unmodifiableMap(newMap), current.selectedPetId(), System.currentTimeMillis(), current.version() + 1);
        });
    }

    public void updateName(UUID ownerId, UUID petId, String name) {
        if (ownerId == null || petId == null) return;
        profiles.computeIfPresent(ownerId, (id, current) -> {
            PetSnapshot old = current.pets().get(petId);
            if (old == null) return current;
            PetSnapshot updated = new PetSnapshot(old.petId(), old.ownerId(), old.definitionId(), name, old.level(), old.experience(), old.availabilityState(), old.selected(), old.spawned());
            Map<UUID, PetSnapshot> newMap = new HashMap<>(current.pets());
            newMap.put(petId, updated);
            return new PlayerPetProfile(ownerId, Collections.unmodifiableMap(newMap), current.selectedPetId(), System.currentTimeMillis(), current.version() + 1);
        });
    }

    public void updateAvailability(UUID ownerId, UUID petId, PetAvailabilityState state) {
        if (ownerId == null || petId == null) return;
        profiles.computeIfPresent(ownerId, (id, current) -> {
            PetSnapshot old = current.pets().get(petId);
            if (old == null) return current;
            PetSnapshot updated = new PetSnapshot(old.petId(), old.ownerId(), old.definitionId(), old.customName(), old.level(), old.experience(), state, old.selected(), old.spawned());
            Map<UUID, PetSnapshot> newMap = new HashMap<>(current.pets());
            newMap.put(petId, updated);
            return new PlayerPetProfile(ownerId, Collections.unmodifiableMap(newMap), current.selectedPetId(), System.currentTimeMillis(), current.version() + 1);
        });
    }

    public void evict(UUID ownerId) {
        beginLoad(ownerId);
        profiles.remove(ownerId);
        inFlightLoads.remove(ownerId);
    }

    public void invalidate(UUID ownerId) {
        evict(ownerId);
    }

    public int size() {
        return profiles.size();
    }

    public void clearAll() {
        ownerGenerations.clear();
        profiles.clear();
        inFlightLoads.clear();
    }
}

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

/**
 * Write-through in-memory cache of player pet profiles.
 *
 * <p>Concurrency guarantees:
 * <ul>
 *   <li>Only one DB load per owner is ever in-flight at a time (see {@link #loadProfileAsync}).</li>
 *   <li>Generation counters ensure stale loads never overwrite newer data.</li>
 *   <li>All mutation methods advance the generation, invalidating concurrent in-flight loads.</li>
 *   <li>{@code inFlightLoads.remove(ownerId, exactFuture)} prevents newer futures from being cleared.</li>
 * </ul>
 */
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

    // ─────────────────────────────────────────────────────────────────────────
    // GENERATION MANAGEMENT
    // ─────────────────────────────────────────────────────────────────────────

    public long beginLoad(UUID ownerId) {
        return ownerGenerations.computeIfAbsent(ownerId, k -> new AtomicLong(0L)).incrementAndGet();
    }

    public boolean completeLoad(UUID ownerId, long generation, PlayerPetProfile profile) {
        AtomicLong currentGen = ownerGenerations.get(ownerId);
        if (currentGen != null && currentGen.get() == generation && profile != null) {
            profiles.put(ownerId, profile);
            return true;
        }
        return false; // stale load — do not apply
    }

    /** Advances the generation for {@code ownerId}, invalidating any in-flight loads. */
    private void advanceGeneration(UUID ownerId) {
        ownerGenerations.computeIfAbsent(ownerId, k -> new AtomicLong(0L)).incrementAndGet();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SYNCHRONOUS LOAD
    // ─────────────────────────────────────────────────────────────────────────

    public PlayerPetProfile loadProfile(UUID ownerId) {
        long gen = beginLoad(ownerId);
        List<PetInstance> pets = petRepository != null ? petRepository.findByOwner(ownerId) : Collections.emptyList();
        Optional<PetSelection> selection = selectionRepository != null ? selectionRepository.findByOwner(ownerId) : Optional.empty();
        UUID selectedId = selection.map(PetSelection::petId).orElse(null);

        Map<UUID, PetSnapshot> petMap = new HashMap<>();
        for (PetInstance pet : pets) {
            boolean isSelected = selectedId != null && selectedId.equals(pet.petId());
            petMap.put(pet.petId(), new PetSnapshot(
                    pet.petId(), pet.ownerId(), pet.definitionId(), pet.customName(),
                    pet.level(), pet.experience(), pet.availabilityState(), isSelected, false));
        }

        // Use actual generation (not hardcoded 1L)
        PlayerPetProfile profile = new PlayerPetProfile(ownerId, Collections.unmodifiableMap(petMap), selectedId, System.currentTimeMillis(), gen);
        completeLoad(ownerId, gen, profile);
        return profile;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ASYNC LOAD — Race-free with computeIfAbsent sentinel
    // ─────────────────────────────────────────────────────────────────────────

    public CompletableFuture<PlayerPetProfile> loadProfileAsync(DatabaseExecutor dbExecutor, UUID ownerId) {
        // Fast path: profile already cached
        PlayerPetProfile existing = profiles.get(ownerId);
        if (existing != null) return CompletableFuture.completedFuture(existing);

        if (dbExecutor == null) {
            // No executor — load synchronously
            try {
                return CompletableFuture.completedFuture(loadProfile(ownerId));
            } catch (Exception e) {
                CompletableFuture<PlayerPetProfile> f = new CompletableFuture<>();
                f.completeExceptionally(e);
                return f;
            }
        }

        // Race-free: create a sentinel future and attempt atomic insertion
        CompletableFuture<PlayerPetProfile> sentinel = new CompletableFuture<>();
        CompletableFuture<PlayerPetProfile> existingFuture = inFlightLoads.putIfAbsent(ownerId, sentinel);
        if (existingFuture != null) {
            // Another thread already started a load — piggyback on it
            return existingFuture;
        }

        // We won the race — start the actual DB work now
        long gen = beginLoad(ownerId);
        dbExecutor.submit(() -> {
            List<PetInstance> pets = petRepository != null ? petRepository.findByOwner(ownerId) : Collections.emptyList();
            Optional<PetSelection> sel = selectionRepository != null ? selectionRepository.findByOwner(ownerId) : Optional.empty();
            UUID selectedId = sel.map(PetSelection::petId).orElse(null);

            Map<UUID, PetSnapshot> petMap = new HashMap<>();
            for (PetInstance pet : pets) {
                boolean isSelected = selectedId != null && selectedId.equals(pet.petId());
                petMap.put(pet.petId(), new PetSnapshot(
                        pet.petId(), pet.ownerId(), pet.definitionId(), pet.customName(),
                        pet.level(), pet.experience(), pet.availabilityState(), isSelected, false));
            }
            return new PlayerPetProfile(ownerId, Collections.unmodifiableMap(new HashMap<>(petMap)), selectedId, System.currentTimeMillis(), gen);
        }).thenAccept(loaded -> {
            completeLoad(ownerId, gen, loaded);
            // Exact reference removal — prevents newer futures from being cleared
            inFlightLoads.remove(ownerId, sentinel);
            sentinel.complete(loaded);
        }).exceptionally(ex -> {
            inFlightLoads.remove(ownerId, sentinel);
            sentinel.completeExceptionally(ex);
            return null;
        });

        return sentinel;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // READ
    // ─────────────────────────────────────────────────────────────────────────

    public Optional<PlayerPetProfile> getProfile(UUID ownerId) {
        return Optional.ofNullable(profiles.get(ownerId));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // WRITE (all advance generation to invalidate stale in-flight loads)
    // ─────────────────────────────────────────────────────────────────────────

    public void putLoadedProfile(PlayerPetProfile profile) {
        if (profile != null) {
            advanceGeneration(profile.ownerId());
            // Defensive immutable copy of the pets map
            Map<UUID, PetSnapshot> defensiveCopy = Collections.unmodifiableMap(new HashMap<>(profile.pets()));
            profiles.put(profile.ownerId(), new PlayerPetProfile(
                    profile.ownerId(), defensiveCopy, profile.selectedPetId(),
                    profile.loadedAt(), profile.version()));
        }
    }

    public void updatePet(UUID ownerId, PetSnapshot pet) {
        if (ownerId == null || pet == null) return;
        advanceGeneration(ownerId);
        profiles.computeIfPresent(ownerId, (id, current) -> {
            Map<UUID, PetSnapshot> newMap = new HashMap<>(current.pets());
            newMap.put(pet.petId(), pet);
            return new PlayerPetProfile(ownerId, Collections.unmodifiableMap(newMap), current.selectedPetId(), System.currentTimeMillis(), current.version() + 1);
        });
    }

    public void removePet(UUID ownerId, UUID petId) {
        if (ownerId == null || petId == null) return;
        advanceGeneration(ownerId);
        profiles.computeIfPresent(ownerId, (id, current) -> {
            Map<UUID, PetSnapshot> newMap = new HashMap<>(current.pets());
            newMap.remove(petId);
            UUID newSelected = Objects.equals(current.selectedPetId(), petId) ? null : current.selectedPetId();
            return new PlayerPetProfile(ownerId, Collections.unmodifiableMap(newMap), newSelected, System.currentTimeMillis(), current.version() + 1);
        });
    }

    public void updateSelection(UUID ownerId, UUID selectedPetId) {
        if (ownerId == null) return;
        advanceGeneration(ownerId);
        profiles.computeIfPresent(ownerId, (id, current) -> {
            Map<UUID, PetSnapshot> newMap = new HashMap<>();
            for (PetSnapshot p : current.pets().values()) {
                boolean isSel = selectedPetId != null && selectedPetId.equals(p.petId());
                // If deselected and was spawned, clear spawned flag too
                boolean isSpawned = isSel && p.spawned();
                newMap.put(p.petId(), new PetSnapshot(p.petId(), p.ownerId(), p.definitionId(),
                        p.customName(), p.level(), p.experience(), p.availabilityState(), isSel, isSpawned));
            }
            return new PlayerPetProfile(ownerId, Collections.unmodifiableMap(newMap), selectedPetId, System.currentTimeMillis(), current.version() + 1);
        });
    }

    public void clearSelection(UUID ownerId) {
        if (ownerId == null) return;
        advanceGeneration(ownerId);
        profiles.computeIfPresent(ownerId, (id, current) -> {
            Map<UUID, PetSnapshot> newMap = new HashMap<>();
            for (PetSnapshot p : current.pets().values()) {
                newMap.put(p.petId(), new PetSnapshot(p.petId(), p.ownerId(), p.definitionId(),
                        p.customName(), p.level(), p.experience(), p.availabilityState(), false, false));
            }
            return new PlayerPetProfile(ownerId, Collections.unmodifiableMap(newMap), null, System.currentTimeMillis(), current.version() + 1);
        });
    }

    // ── Stage 13: Spawned State ───────────────────────────────────────────────

    /** Sets spawned=true for {@code petId}, spawned=false for all other pets of this owner. */
    public void updateSpawnedPet(UUID ownerId, UUID petId) {
        if (ownerId == null || petId == null) return;
        advanceGeneration(ownerId);
        profiles.computeIfPresent(ownerId, (id, current) -> {
            Map<UUID, PetSnapshot> newMap = new HashMap<>();
            for (PetSnapshot p : current.pets().values()) {
                boolean isSpawned = p.petId().equals(petId);
                newMap.put(p.petId(), new PetSnapshot(p.petId(), p.ownerId(), p.definitionId(),
                        p.customName(), p.level(), p.experience(), p.availabilityState(), p.selected(), isSpawned));
            }
            return new PlayerPetProfile(ownerId, Collections.unmodifiableMap(newMap), current.selectedPetId(), System.currentTimeMillis(), current.version() + 1);
        });
    }

    /** Sets spawned=false for ALL pets of this owner (e.g. after dismiss or world-change despawn). */
    public void clearSpawnedPet(UUID ownerId) {
        if (ownerId == null) return;
        advanceGeneration(ownerId);
        profiles.computeIfPresent(ownerId, (id, current) -> {
            Map<UUID, PetSnapshot> newMap = new HashMap<>();
            for (PetSnapshot p : current.pets().values()) {
                newMap.put(p.petId(), new PetSnapshot(p.petId(), p.ownerId(), p.definitionId(),
                        p.customName(), p.level(), p.experience(), p.availabilityState(), p.selected(), false));
            }
            return new PlayerPetProfile(ownerId, Collections.unmodifiableMap(newMap), current.selectedPetId(), System.currentTimeMillis(), current.version() + 1);
        });
    }

    /**
     * Atomically updates both selection and spawned state.
     * selected=true and spawned=true only for the respective pet IDs.
     * {@code spawnedPetId} may be null (e.g. during world-change interim).
     */
    public void updateRuntimeState(UUID ownerId, UUID selectedPetId, UUID spawnedPetId) {
        if (ownerId == null) return;
        advanceGeneration(ownerId);
        profiles.computeIfPresent(ownerId, (id, current) -> {
            Map<UUID, PetSnapshot> newMap = new HashMap<>();
            for (PetSnapshot p : current.pets().values()) {
                boolean isSel = selectedPetId != null && p.petId().equals(selectedPetId);
                boolean isSpawned = spawnedPetId != null && p.petId().equals(spawnedPetId);
                newMap.put(p.petId(), new PetSnapshot(p.petId(), p.ownerId(), p.definitionId(),
                        p.customName(), p.level(), p.experience(), p.availabilityState(), isSel, isSpawned));
            }
            return new PlayerPetProfile(ownerId, Collections.unmodifiableMap(newMap), selectedPetId, System.currentTimeMillis(), current.version() + 1);
        });
    }

    // ── Other mutations ───────────────────────────────────────────────────────

    public void updateExperience(UUID ownerId, UUID petId, int level, long experience) {
        if (ownerId == null || petId == null) return;
        advanceGeneration(ownerId);
        profiles.computeIfPresent(ownerId, (id, current) -> {
            PetSnapshot old = current.pets().get(petId);
            if (old == null) return current;
            PetSnapshot updated = new PetSnapshot(old.petId(), old.ownerId(), old.definitionId(),
                    old.customName(), level, experience, old.availabilityState(), old.selected(), old.spawned());
            Map<UUID, PetSnapshot> newMap = new HashMap<>(current.pets());
            newMap.put(petId, updated);
            return new PlayerPetProfile(ownerId, Collections.unmodifiableMap(newMap), current.selectedPetId(), System.currentTimeMillis(), current.version() + 1);
        });
    }

    public void updateName(UUID ownerId, UUID petId, String name) {
        if (ownerId == null || petId == null) return;
        advanceGeneration(ownerId);
        profiles.computeIfPresent(ownerId, (id, current) -> {
            PetSnapshot old = current.pets().get(petId);
            if (old == null) return current;
            PetSnapshot updated = new PetSnapshot(old.petId(), old.ownerId(), old.definitionId(),
                    name, old.level(), old.experience(), old.availabilityState(), old.selected(), old.spawned());
            Map<UUID, PetSnapshot> newMap = new HashMap<>(current.pets());
            newMap.put(petId, updated);
            return new PlayerPetProfile(ownerId, Collections.unmodifiableMap(newMap), current.selectedPetId(), System.currentTimeMillis(), current.version() + 1);
        });
    }

    public void updateDefinition(UUID ownerId, UUID petId, String definitionId) {
        if (ownerId == null || petId == null || definitionId == null) return;
        advanceGeneration(ownerId);
        profiles.computeIfPresent(ownerId, (id, current) -> {
            PetSnapshot old = current.pets().get(petId);
            if (old == null) return current;
            PetSnapshot updated = new PetSnapshot(old.petId(), old.ownerId(), definitionId,
                    old.customName(), old.level(), old.experience(), old.availabilityState(), old.selected(), old.spawned());
            Map<UUID, PetSnapshot> newMap = new HashMap<>(current.pets());
            newMap.put(petId, updated);
            return new PlayerPetProfile(ownerId, Collections.unmodifiableMap(newMap), current.selectedPetId(), System.currentTimeMillis(), current.version() + 1);
        });
    }

    public void updateAvailability(UUID ownerId, UUID petId, PetAvailabilityState state) {
        if (ownerId == null || petId == null) return;
        advanceGeneration(ownerId);
        profiles.computeIfPresent(ownerId, (id, current) -> {
            PetSnapshot old = current.pets().get(petId);
            if (old == null) return current;
            PetSnapshot updated = new PetSnapshot(old.petId(), old.ownerId(), old.definitionId(),
                    old.customName(), old.level(), old.experience(), state, old.selected(), old.spawned());
            Map<UUID, PetSnapshot> newMap = new HashMap<>(current.pets());
            newMap.put(petId, updated);
            return new PlayerPetProfile(ownerId, Collections.unmodifiableMap(newMap), current.selectedPetId(), System.currentTimeMillis(), current.version() + 1);
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // EVICTION
    // ─────────────────────────────────────────────────────────────────────────

    public void evict(UUID ownerId) {
        advanceGeneration(ownerId);
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

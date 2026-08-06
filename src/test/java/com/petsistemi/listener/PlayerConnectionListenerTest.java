package com.petsistemi.listener;

import com.petsistemi.api.PetSnapshot;
import com.petsistemi.bootstrap.FakeMainThreadDispatcher;
import com.petsistemi.domain.PetAvailabilityState;
import com.petsistemi.domain.PetInstance;
import com.petsistemi.domain.PetSelection;
import com.petsistemi.persistence.DatabaseExecutor;
import com.petsistemi.persistence.PetRepository;
import com.petsistemi.persistence.PetSelectionRepository;
import com.petsistemi.persistence.PlayerPetProfile;
import com.petsistemi.persistence.PlayerPetProfileCache;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class PlayerConnectionListenerTest {

    private DatabaseExecutor dbExecutor;
    private FakeMainThreadDispatcher mainThreadDispatcher;
    private PlayerPetProfileCache profileCache;
    private UUID ownerId;
    private UUID petId;

    @BeforeEach
    void setUp() {
        dbExecutor = new DatabaseExecutor(Logger.getLogger("TestPlayerConnectionListener"));
        mainThreadDispatcher = new FakeMainThreadDispatcher();
        ownerId = UUID.randomUUID();
        petId = UUID.randomUUID();

        PetRepository petRepository = new PetRepository() {
            @Override public Optional<PetInstance> findById(UUID id) { return Optional.empty(); }
            @Override public List<PetInstance> findByOwner(UUID id) { return List.of(); }
            @Override public Optional<PetInstance> findActiveByOwner(UUID ownerId) { return Optional.empty(); }
            @Override public void insert(PetInstance pet) {}
            @Override public void update(PetInstance pet) {}
            @Override public void delete(UUID petId) {}
            @Override public void setActivePet(UUID ownerId, UUID petId) {}
            @Override public void clearActivePet(UUID ownerId) {}
            @Override public void switchActivePet(UUID ownerId, UUID previousPetId, UUID newPetId) {}
            @Override public void clearActivePetAndSetAvailable(UUID ownerId, UUID petId) {}
            @Override public void restoreActivePet(UUID ownerId, UUID previousPetId, UUID failedPetId) {}
        };

        PetSelectionRepository selectionRepository = new PetSelectionRepository() {
            @Override public Optional<PetSelection> findByOwner(UUID ownerId) { return Optional.empty(); }
            @Override public void select(UUID ownerId, UUID petId) {}
            @Override public void clear(UUID ownerId) {}
            @Override public void switchSelection(UUID ownerId, UUID previousPetId, UUID newPetId) {}
            @Override public void updateFollowMode(UUID ownerId, com.petsistemi.domain.PetFollowMode followMode) {}
        };

        profileCache = new PlayerPetProfileCache(petRepository, selectionRepository);
    }

    @AfterEach
    void tearDown() {
        if (dbExecutor != null && !dbExecutor.isClosed()) {
            dbExecutor.close();
        }
    }

    @Test
    void testStaleProfileLoadDiscardedAfterQuit() throws Exception {
        long gen = profileCache.beginLoad(ownerId);

        // Player quits -> invalidates owner cache and increments generation counter
        profileCache.invalidate(ownerId);

        PetSnapshot snapshot = new PetSnapshot(petId, ownerId, "wolf", "Kurt", 1, 0, PetAvailabilityState.AVAILABLE, false, false);
        PlayerPetProfile staleProfile = new PlayerPetProfile(ownerId, Map.of(petId, snapshot), null, System.currentTimeMillis(), 1L);

        boolean published = profileCache.completeLoad(ownerId, gen, staleProfile);
        assertFalse(published, "Quit gerçekleştiği için eski profil yüklemesi kabul edilmemeli");
        assertTrue(profileCache.getProfile(ownerId).isEmpty());
    }

    @Test
    void testProfileLoadAsyncPublishesToCache() throws Exception {
        CompletableFuture<PlayerPetProfile> future = profileCache.loadProfileAsync(dbExecutor, ownerId);
        PlayerPetProfile profile = future.get(5, TimeUnit.SECONDS);

        assertNotNull(profile);
        assertEquals(ownerId, profile.ownerId());
        assertTrue(profileCache.getProfile(ownerId).isPresent());
    }
}

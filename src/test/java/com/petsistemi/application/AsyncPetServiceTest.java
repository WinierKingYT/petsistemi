package com.petsistemi.application;

import com.petsistemi.api.PetSnapshot;
import com.petsistemi.api.result.PetRenameResult;
import com.petsistemi.bootstrap.FakeMainThreadDispatcher;
import com.petsistemi.definition.PetDefinitionRegistry;
import com.petsistemi.domain.PetAvailabilityState;
import com.petsistemi.domain.PetDefinition;
import com.petsistemi.domain.PetInstance;
import com.petsistemi.domain.PetSelection;
import com.petsistemi.persistence.DatabaseExecutor;
import com.petsistemi.persistence.PetRepository;
import com.petsistemi.persistence.PetSelectionRepository;
import com.petsistemi.persistence.PlayerPetProfile;
import com.petsistemi.persistence.PlayerPetProfileCache;
import com.petsistemi.runtime.ActivePetRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class AsyncPetServiceTest {

    private DatabaseExecutor dbExecutor;
    private FakeMainThreadDispatcher mainThreadDispatcher;
    private PlayerPetProfileCache profileCache;
    private DefaultPetService petService;
    private UUID ownerId;
    private UUID petId;

    @BeforeEach
    void setUp() {
        dbExecutor = new DatabaseExecutor(Logger.getLogger("TestAsyncPetService"));
        mainThreadDispatcher = new FakeMainThreadDispatcher();
        ownerId = UUID.randomUUID();
        petId = UUID.randomUUID();

        PetRepository repository = new PetRepository() {
            private PetInstance instance = new PetInstance(petId, ownerId, "wolf", "Kurt", 1, 0, PetAvailabilityState.AVAILABLE, System.currentTimeMillis(), System.currentTimeMillis());
            @Override public Optional<PetInstance> findById(UUID id) { return id.equals(petId) ? Optional.ofNullable(instance) : Optional.empty(); }
            @Override public List<PetInstance> findByOwner(UUID id) { return id.equals(ownerId) && instance != null ? List.of(instance) : List.of(); }
            @Override public void insert(PetInstance pet) { this.instance = pet; }
            @Override public void update(PetInstance pet) { this.instance = pet; }
            @Override public void delete(UUID id) { if (id.equals(petId)) instance = null; }
            @Override public Optional<PetInstance> findActiveByOwner(UUID ownerId) { return Optional.empty(); }
            @Override public void setActivePet(UUID ownerId, UUID petId) {}
            @Override public void clearActivePet(UUID ownerId) {}
            @Override public void switchActivePet(UUID ownerId, UUID oldPetId, UUID newPetId) {}
            @Override public void restoreActivePet(UUID ownerId, UUID oldPetId, UUID newPetId) {}
            @Override public void clearActivePetAndSetAvailable(UUID ownerId, UUID petId) {}
        };

        PetSelectionRepository selectionRepository = new PetSelectionRepository() {
            @Override public Optional<PetSelection> findByOwner(UUID id) { return Optional.empty(); }
            @Override public void select(UUID ownerId, UUID petId) {}
            @Override public void clear(UUID ownerId) {}
            @Override public void switchSelection(UUID ownerId, UUID previousPetId, UUID newPetId) {}
        };

        profileCache = new PlayerPetProfileCache(repository, selectionRepository);

        PetDefinition def = new PetDefinition("wolf", "Kurt", List.of("WOLF"), "DOG", true, true, true, true, true, true, 100, true, List.of());
        PetDefinitionRegistry definitionRegistry = new PetDefinitionRegistry() {
            @Override public Optional<PetDefinition> find(String id) { return Optional.of(def); }
            @Override public Collection<PetDefinition> getAll() { return List.of(def); }
            @Override public void reload() {}
        };

        ActivePetRegistry activePetRegistry = new ActivePetRegistry();

        petService = new DefaultPetService(
                null,
                repository,
                selectionRepository,
                definitionRegistry,
                activePetRegistry,
                null,
                dbExecutor,
                mainThreadDispatcher,
                profileCache,
                null
        );
    }

    @AfterEach
    void tearDown() {
        if (dbExecutor != null && !dbExecutor.isClosed()) {
            dbExecutor.close();
        }
    }

    @Test
    void testGetOwnedPetsAsyncUsesCacheHit() throws Exception {
        PetSnapshot snapshot = new PetSnapshot(petId, ownerId, "wolf", "Kurt", 1, 0, PetAvailabilityState.AVAILABLE, false, false);
        PlayerPetProfile profile = new PlayerPetProfile(ownerId, Map.of(petId, snapshot), null, System.currentTimeMillis(), 1L);
        profileCache.putLoadedProfile(profile);

        CompletableFuture<Collection<PetSnapshot>> future = petService.getOwnedPetsAsync(ownerId);
        Collection<PetSnapshot> result = future.get(5, TimeUnit.SECONDS);

        assertEquals(1, result.size());
        assertEquals("Kurt", result.iterator().next().customName());
    }

    @Test
    void testRenameAsyncUpdatesCacheOnSuccess() throws Exception {
        PetSnapshot snapshot = new PetSnapshot(petId, ownerId, "wolf", "Kurt", 1, 0, PetAvailabilityState.AVAILABLE, false, false);
        PlayerPetProfile profile = new PlayerPetProfile(ownerId, Map.of(petId, snapshot), null, System.currentTimeMillis(), 1L);
        profileCache.putLoadedProfile(profile);

        CompletableFuture<PetRenameResult> renameFuture = petService.renameAsync(petId, "Fırtına");
        PetRenameResult result = renameFuture.get(5, TimeUnit.SECONDS);

        assertTrue(result.success());
        PlayerPetProfile updatedProfile = profileCache.getProfile(ownerId).orElseThrow();
        assertEquals("Fırtına", updatedProfile.pets().get(petId).customName());
    }
}

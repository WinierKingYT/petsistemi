package com.petsistemi.application;

import com.petsistemi.bootstrap.FakeMainThreadDispatcher;
import com.petsistemi.definition.PetDefinitionRegistry;
import com.petsistemi.domain.PetAvailabilityState;
import com.petsistemi.domain.PetDefinition;
import com.petsistemi.domain.PetInstance;
import com.petsistemi.domain.PetSelection;
import com.petsistemi.persistence.DatabaseExecutor;
import com.petsistemi.persistence.PetRepository;
import com.petsistemi.persistence.PetSelectionRepository;
import com.petsistemi.persistence.PlayerPetProfileCache;
import com.petsistemi.runtime.ActivePet;
import com.petsistemi.runtime.ActivePetRegistry;
import com.petsistemi.runtime.PetRuntimeCoordinator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the watchdog recovery path: a dead/unloaded pet is re-summoned only
 * when the DB selection still points to it and the pet is still available.
 */
class PetRecoveryPipelineTest {

    private DatabaseExecutor dbExecutor;
    private FakeMainThreadDispatcher mainThreadDispatcher;
    private PetRepository petRepository;
    private PetSelectionRepository selectionRepository;
    private PetRuntimeOperationService service;
    private UUID ownerId;
    private UUID petId;

    @BeforeEach
    void setUp() {
        dbExecutor = new DatabaseExecutor(Logger.getLogger("PetRecoveryPipelineTest"));
        mainThreadDispatcher = new FakeMainThreadDispatcher();
        ownerId = UUID.randomUUID();
        petId = UUID.randomUUID();

        petRepository = new PetRepository() {
            @Override public Optional<PetInstance> findById(UUID id) {
                return Optional.of(new PetInstance(petId, ownerId, "wolf", "Kurt", 1, 0, PetAvailabilityState.AVAILABLE, 1L, 1L));
            }
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
            @Override public void disablePetTransactional(UUID ownerId, PetInstance updatedPet) {}
            @Override public void removePetTransactional(UUID ownerId, UUID petId) {}
        };

        selectionRepository = new PetSelectionRepository() {
            @Override public Optional<PetSelection> findByOwner(UUID ownerId) { return Optional.of(new PetSelection(ownerId, petId, 1L)); }
            @Override public void select(UUID ownerId, UUID petId) {}
            @Override public void clear(UUID ownerId) {}
            @Override public void switchSelection(UUID ownerId, UUID previousPetId, UUID newPetId) {}
        };

        PetDefinition def = new PetDefinition("wolf", "Kurt", List.of("WOLF"), "DOG", true, true, true, true, true, true, 100, true, List.of());
        PetDefinitionRegistry defRegistry = new PetDefinitionRegistry() {
            @Override public Optional<PetDefinition> find(String id) { return Optional.of(def); }
            @Override public Collection<PetDefinition> getAll() { return List.of(def); }
            @Override public void reload() {}
        };

        ActivePetRegistry activeRegistry = new ActivePetRegistry();
        PetRuntimeCoordinator coordinator = new PetRuntimeCoordinator(null, defRegistry, activeRegistry, null, null);
        PlayerPetProfileCache profileCache = new PlayerPetProfileCache(petRepository, selectionRepository);

        service = new PetRuntimeOperationService(
                null, petRepository, selectionRepository, defRegistry, coordinator, profileCache, dbExecutor, mainThreadDispatcher
        );
    }

    @AfterEach
    void tearDown() {
        if (dbExecutor != null && !dbExecutor.isClosed()) {
            dbExecutor.close();
        }
    }

    private ActivePet activePet() {
        return new ActivePet(petId, ownerId, "wolf", 1, UUID.randomUUID(), null, com.petsistemi.domain.PetRuntimeState.ACTIVE);
    }

    private org.bukkit.entity.Player onlineOwner() {
        org.bukkit.entity.Player player = org.mockito.Mockito.mock(org.bukkit.entity.Player.class);
        org.mockito.Mockito.when(player.getUniqueId()).thenReturn(ownerId);
        org.mockito.Mockito.when(player.isOnline()).thenReturn(true);
        return player;
    }

    @Test
    void testRecoveryStartsSummonPipelineWhenSelectionMatches() throws Exception {
        long before = mainThreadDispatcher.submittedCount();

        service.recoverPetAsync(activePet(), onlineOwner());

        // Drain DB executor and main worker queue
        dbExecutor.runAsync(() -> {}).get(5, TimeUnit.SECONDS);
        Thread.sleep(200);

        // Summon phase 2 runs on the main thread → submitted actions must have increased
        assertTrue(mainThreadDispatcher.submittedCount() > before,
                "Recovery MUST start the summon pipeline when the selection matches.");
    }

    @Test
    void testRecoveryDoesNotSummonWhenPetDisabled() throws Exception {
        petRepository = new PetRepository() {
            @Override public Optional<PetInstance> findById(UUID id) {
                return Optional.of(new PetInstance(petId, ownerId, "wolf", "Kurt", 1, 0, PetAvailabilityState.DISABLED, 1L, 1L));
            }
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
            @Override public void disablePetTransactional(UUID ownerId, PetInstance updatedPet) {}
            @Override public void removePetTransactional(UUID ownerId, UUID petId) {}
        };
        ActivePetRegistry activeRegistry = new ActivePetRegistry();
        PlayerPetProfileCache profileCache = new PlayerPetProfileCache(petRepository, selectionRepository);
        service = new PetRuntimeOperationService(
                null, petRepository, selectionRepository, null, new PetRuntimeCoordinator(null, null, activeRegistry, null, null),
                profileCache, dbExecutor, mainThreadDispatcher
        );

        long before = mainThreadDispatcher.submittedCount();

        service.recoverPetAsync(activePet(), onlineOwner());

        dbExecutor.runAsync(() -> {}).get(5, TimeUnit.SECONDS);
        Thread.sleep(200);

        assertEquals(before, mainThreadDispatcher.submittedCount(),
                "Recovery MUST NOT start a summon for a DISABLED pet.");
    }

    @Test
    void testRecoverySkipsSummonWhenSelectionPointsElsewhere() throws Exception {
        selectionRepository = new PetSelectionRepository() {
            @Override public Optional<PetSelection> findByOwner(UUID ownerId) {
                return Optional.of(new PetSelection(ownerId, UUID.randomUUID(), 1L));
            }
            @Override public void select(UUID ownerId, UUID petId) {}
            @Override public void clear(UUID ownerId) {}
            @Override public void switchSelection(UUID ownerId, UUID previousPetId, UUID newPetId) {}
        };
        ActivePetRegistry activeRegistry = new ActivePetRegistry();
        PlayerPetProfileCache profileCache = new PlayerPetProfileCache(petRepository, selectionRepository);
        service = new PetRuntimeOperationService(
                null, petRepository, selectionRepository, null, new PetRuntimeCoordinator(null, null, activeRegistry, null, null),
                profileCache, dbExecutor, mainThreadDispatcher
        );

        long before = mainThreadDispatcher.submittedCount();

        service.recoverPetAsync(activePet(), onlineOwner());

        dbExecutor.runAsync(() -> {}).get(5, TimeUnit.SECONDS);
        Thread.sleep(200);

        assertEquals(before, mainThreadDispatcher.submittedCount(),
                "Recovery MUST NOT summon when the DB selection points to another pet.");
    }
}

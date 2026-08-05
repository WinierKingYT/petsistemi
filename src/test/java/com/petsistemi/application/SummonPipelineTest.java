package com.petsistemi.application;

import com.petsistemi.api.PetSnapshot;
import com.petsistemi.api.result.PetSummonResult;
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
import com.petsistemi.runtime.ActivePetRegistry;
import com.petsistemi.runtime.PetRuntimeCoordinator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class SummonPipelineTest {

    private DatabaseExecutor dbExecutor;
    private FakeMainThreadDispatcher mainThreadDispatcher;
    private PlayerPetProfileCache profileCache;
    private ActivePetRegistry activeRegistry;
    private UUID ownerId;
    private UUID petId;

    @BeforeEach
    void setUp() {
        dbExecutor = new DatabaseExecutor(Logger.getLogger("SummonPipelineTest"));
        mainThreadDispatcher = new FakeMainThreadDispatcher();
        ownerId = UUID.randomUUID();
        petId = UUID.randomUUID();
        activeRegistry = new ActivePetRegistry();
    }

    @AfterEach
    void tearDown() {
        if (dbExecutor != null && !dbExecutor.isClosed()) {
            dbExecutor.close();
        }
    }

    @Test
    void testSummonFailsWhenPetDisabled() throws Exception {
        PetRepository petRepository = new PetRepository() {
            @Override public Optional<PetInstance> findById(UUID id) { return Optional.of(new PetInstance(petId, ownerId, "wolf", "Kurt", 1, 0, PetAvailabilityState.DISABLED, System.currentTimeMillis(), System.currentTimeMillis())); }
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
        };

        PetDefinition def = new PetDefinition("wolf", "Kurt", List.of("WOLF"), "DOG", true, true, true, true, true, true, 100, true, List.of());
        PetDefinitionRegistry defRegistry = new PetDefinitionRegistry() {
            @Override public Optional<PetDefinition> find(String id) { return Optional.of(def); }
            @Override public Collection<PetDefinition> getAll() { return List.of(def); }
            @Override public void reload() {}
        };

        PetRuntimeCoordinator coordinator = new PetRuntimeCoordinator(null, defRegistry, activeRegistry, null, null);
        profileCache = new PlayerPetProfileCache(petRepository, selectionRepository);

        PetRuntimeOperationService service = new PetRuntimeOperationService(
                null, petRepository, selectionRepository, defRegistry, coordinator, profileCache, dbExecutor, mainThreadDispatcher
        );

        // Simulated offline player object check in pure unit test
        org.bukkit.entity.Player mockPlayer = org.mockito.Mockito.mock(org.bukkit.entity.Player.class);
        org.mockito.Mockito.when(mockPlayer.getUniqueId()).thenReturn(ownerId);
        org.mockito.Mockito.when(mockPlayer.isOnline()).thenReturn(true);

        CompletableFuture<PetSummonResult> future = service.summonAsync(mockPlayer, petId);
        PetSummonResult result = future.get(5, TimeUnit.SECONDS);

        assertFalse(result.success());
        assertTrue(result.message().contains("DISABLED"));
    }
}

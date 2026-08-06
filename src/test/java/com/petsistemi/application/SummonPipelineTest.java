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
            @Override public void updateFollowMode(UUID ownerId, com.petsistemi.domain.PetFollowMode followMode) {}
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

    @Test
    void testSummonDeniedWhenDefinitionPermissionMissing() throws Exception {
        PetSummonResult result = summonWithPermission("companionpets.pet.wolf", false);

        assertFalse(result.success(), "yetkisi olmayan oyuncu peti çağıramamalı");
        assertTrue(result.message().contains("companionpets.pet.wolf"),
                () -> "mesaj eksik yetkiyi belirtmeli: " + result.message());
    }

    @Test
    void testSummonProceedsPastPermissionGateWhenGranted() throws Exception {
        PetSummonResult result = summonWithPermission("companionpets.pet.wolf", true);

        assertFalse(result.message().contains("companionpets.pet.wolf"),
                () -> "yetki verildiğinde yetki reddi dönmemeli: " + result.message());
    }

    @Test
    void testSummonSkipsPermissionGateWhenDefinitionHasNone() throws Exception {
        PetSummonResult result = summonWithPermission(null, false);

        assertFalse(result.message().contains("yetkiniz yok"),
                () -> "permission tanımlamayan pet kısıtlanmamalı: " + result.message());
    }

    /** Drives summonAsync for an AVAILABLE pet whose definition carries the given permission node. */
    private PetSummonResult summonWithPermission(String permission, boolean granted) throws Exception {
        PetInstance pet = new PetInstance(petId, ownerId, "wolf", "Kurt", 1, 0,
                PetAvailabilityState.AVAILABLE, System.currentTimeMillis(), System.currentTimeMillis());

        PetRepository petRepository = new PetRepository() {
            @Override public Optional<PetInstance> findById(UUID id) { return Optional.of(pet); }
            @Override public List<PetInstance> findByOwner(UUID id) { return List.of(pet); }
            @Override public Optional<PetInstance> findActiveByOwner(UUID ownerId) { return Optional.empty(); }
            @Override public void insert(PetInstance p) {}
            @Override public void update(PetInstance p) {}
            @Override public void delete(UUID id) {}
            @Override public void setActivePet(UUID owner, UUID id) {}
            @Override public void clearActivePet(UUID owner) {}
            @Override public void switchActivePet(UUID owner, UUID previousPetId, UUID newPetId) {}
            @Override public void clearActivePetAndSetAvailable(UUID owner, UUID id) {}
            @Override public void restoreActivePet(UUID owner, UUID previousPetId, UUID failedPetId) {}
        };

        PetSelectionRepository selectionRepository = new PetSelectionRepository() {
            @Override public Optional<PetSelection> findByOwner(UUID owner) { return Optional.empty(); }
            @Override public void select(UUID owner, UUID id) {}
            @Override public void clear(UUID owner) {}
            @Override public void switchSelection(UUID owner, UUID previousPetId, UUID newPetId) {}
            @Override public void updateFollowMode(UUID owner, com.petsistemi.domain.PetFollowMode followMode) {}
        };

        PetDefinition def = new PetDefinition("wolf", "Kurt", List.of(), "WOLF",
                false, false, true, false, true, true, 100, true, List.of("{pet_name}"),
                null, null, null, null, null, null, null, permission);
        PetDefinitionRegistry defRegistry = new PetDefinitionRegistry() {
            @Override public Optional<PetDefinition> find(String id) { return Optional.of(def); }
            @Override public Collection<PetDefinition> getAll() { return List.of(def); }
            @Override public void reload() {}
        };

        PetRuntimeCoordinator coordinator = new PetRuntimeCoordinator(null, defRegistry, activeRegistry, null, null);
        profileCache = new PlayerPetProfileCache(petRepository, selectionRepository);

        PetRuntimeOperationService service = new PetRuntimeOperationService(
                null, petRepository, selectionRepository, defRegistry, coordinator, profileCache,
                dbExecutor, mainThreadDispatcher);

        org.bukkit.entity.Player mockPlayer = org.mockito.Mockito.mock(org.bukkit.entity.Player.class);
        org.mockito.Mockito.when(mockPlayer.getUniqueId()).thenReturn(ownerId);
        org.mockito.Mockito.when(mockPlayer.isOnline()).thenReturn(true);
        org.mockito.Mockito.when(mockPlayer.hasPermission(org.mockito.ArgumentMatchers.anyString())).thenReturn(granted);

        return service.summonAsync(mockPlayer, petId).get(5, TimeUnit.SECONDS);
    }
}

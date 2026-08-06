package com.petsistemi.application;

import com.petsistemi.api.result.PetDisableResult;
import com.petsistemi.api.result.PetRemoveResult;
import com.petsistemi.domain.PetAvailabilityState;
import com.petsistemi.domain.PetInstance;
import com.petsistemi.domain.PetSelection;
import com.petsistemi.persistence.PetRepository;
import com.petsistemi.persistence.PetSelectionRepository;
import com.petsistemi.runtime.ActivePet;
import com.petsistemi.runtime.ActivePetRegistry;
import com.petsistemi.runtime.PetRuntimeCoordinator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class DefaultPetServiceTargetTest {

    private TestPetRepository petRepository;
    private TestSelectionRepository selectionRepository;
    private ActivePetRegistry activePetRegistry;
    private TestCoordinator coordinator;
    private DefaultPetService petService;

    private UUID ownerId;
    private UUID petAId;
    private UUID petBId;

    @BeforeEach
    void setUp() {
        selectionRepository = new TestSelectionRepository();
        petRepository = new TestPetRepository(selectionRepository);
        activePetRegistry = new ActivePetRegistry();
        coordinator = new TestCoordinator();

        ownerId = UUID.randomUUID();
        petAId = UUID.randomUUID();
        petBId = UUID.randomUUID();

        PetInstance petA = new PetInstance(petAId, ownerId, "wolf", "PetA", 1, 0, PetAvailabilityState.AVAILABLE, 100, 100);
        PetInstance petB = new PetInstance(petBId, ownerId, "cat", "PetB", 1, 0, PetAvailabilityState.AVAILABLE, 100, 100);

        petRepository.insert(petA);
        petRepository.insert(petB);

        // Owner currently has Pet A selected and active in runtime
        selectionRepository.select(ownerId, petAId);
        activePetRegistry.register(new ActivePet(petAId, ownerId, null, null, com.petsistemi.domain.PetRuntimeState.ACTIVE));

        com.petsistemi.persistence.DatabaseExecutor dbExecutor = new com.petsistemi.persistence.DatabaseExecutor(java.util.logging.Logger.getLogger("TargetTest"));
        com.petsistemi.bootstrap.FakeMainThreadDispatcher dispatcher = new com.petsistemi.bootstrap.FakeMainThreadDispatcher();
        petService = new DefaultPetService(null, petRepository, selectionRepository, null, activePetRegistry, null, dbExecutor, dispatcher, null, null);
    }

    @Test
    void disablingUnselectedPetBDoesNotDespawnOrClearActivePetA() {
        PetDisableResult result = petService.disablePet(petBId);

        assertTrue(result.success());
        assertFalse(coordinator.dismissCalledForOwner, "Coordinator dismiss must NOT be called for owner when disabling unselected Pet B");
        assertNotNull(selectionRepository.findByOwner(ownerId).orElse(null), "Selection for Pet A must remain intact");
        assertEquals(petAId, selectionRepository.findByOwner(ownerId).get().petId());
        assertEquals(PetAvailabilityState.DISABLED, petRepository.findById(petBId).get().availabilityState());
    }

    @Test
    void removingUnselectedPetBDoesNotDespawnOrClearActivePetA() {
        PetRemoveResult result = petService.removePet(petBId);

        assertTrue(result.success());
        assertFalse(coordinator.dismissCalledForOwner, "Coordinator dismiss must NOT be called for owner when removing unselected Pet B");
        assertNotNull(selectionRepository.findByOwner(ownerId).orElse(null), "Selection for Pet A must remain intact");
        assertEquals(petAId, selectionRepository.findByOwner(ownerId).get().petId());
        assertTrue(petRepository.findById(petBId).isEmpty());
    }

    @Test
    void disablingSelectedPetADespawnsAndClearsSelection() {
        PetDisableResult result = petService.disablePet(petAId);

        assertTrue(result.success());
        assertTrue(selectionRepository.findByOwner(ownerId).isEmpty(), "Selection MUST be cleared when disabling selected Pet A");
        assertEquals(PetAvailabilityState.DISABLED, petRepository.findById(petAId).get().availabilityState());
    }

    @Test
    void dbFailureOnDisableDoesNotDespawnActivePetA() {
        petRepository.shouldFailUpdate = true;

        PetDisableResult result = petService.disablePet(petAId);

        assertFalse(result.success());
        assertFalse(coordinator.dismissCalledForOwner, "Coordinator dismiss must NOT be called if DB update fails");
    }

    private static class TestPetRepository implements PetRepository {
        private final Map<UUID, PetInstance> map = new HashMap<>();
        private final TestSelectionRepository selectionRepo;
        boolean shouldFailUpdate = false;

        public TestPetRepository(TestSelectionRepository selectionRepo) {
            this.selectionRepo = selectionRepo;
        }

        @Override public Optional<PetInstance> findById(UUID petId) { return Optional.ofNullable(map.get(petId)); }
        @Override public List<PetInstance> findByOwner(UUID ownerId) { return map.values().stream().filter(p -> p.ownerId().equals(ownerId)).toList(); }
        @Override public Optional<PetInstance> findActiveByOwner(UUID ownerId) { return Optional.empty(); }
        @Override public void insert(PetInstance pet) { map.put(pet.petId(), pet); }
        @Override public void update(PetInstance pet) {
            if (shouldFailUpdate) throw new RuntimeException("DB update simulated failure");
            map.put(pet.petId(), pet);
        }
        @Override public void delete(UUID petId) { map.remove(petId); }
        @Override public void setActivePet(UUID ownerId, UUID petId) {}
        @Override public void clearActivePet(UUID ownerId) {}
        @Override public void switchActivePet(UUID ownerId, UUID previousPetId, UUID newPetId) {}
        @Override public void clearActivePetAndSetAvailable(UUID ownerId, UUID petId) {}
        @Override public void restoreActivePet(UUID ownerId, UUID previousPetId, UUID failedPetId) {}

        @Override
        public void disablePetTransactional(UUID clearSelectionOwnerId, PetInstance updatedPet) {
            if (shouldFailUpdate) throw new RuntimeException("DB update simulated failure");
            if (clearSelectionOwnerId != null) selectionRepo.clear(clearSelectionOwnerId);
            map.put(updatedPet.petId(), updatedPet);
        }

        @Override
        public void removePetTransactional(UUID clearSelectionOwnerId, UUID targetPetId) {
            if (clearSelectionOwnerId != null) selectionRepo.clear(clearSelectionOwnerId);
            map.remove(targetPetId);
        }
    }

    private static class TestSelectionRepository implements PetSelectionRepository {
        private final Map<UUID, PetSelection> map = new HashMap<>();

        @Override public Optional<PetSelection> findByOwner(UUID ownerId) { return Optional.ofNullable(map.get(ownerId)); }
        @Override public void select(UUID ownerId, UUID petId) { map.put(ownerId, new PetSelection(ownerId, petId, System.currentTimeMillis())); }
        @Override public void clear(UUID ownerId) { map.remove(ownerId); }
        @Override public void switchSelection(UUID ownerId, UUID previousPetId, UUID newPetId) { map.put(ownerId, new PetSelection(ownerId, newPetId, System.currentTimeMillis())); }
        @Override public void updateFollowMode(UUID ownerId, com.petsistemi.domain.PetFollowMode followMode) {
            map.computeIfPresent(ownerId, (id, sel) -> sel.withFollowMode(followMode));
        }
    }

    private static class TestCoordinator extends PetRuntimeCoordinator {
        boolean dismissCalledForOwner = false;

        public TestCoordinator() {
            super(null, null, null, null, null);
        }

        @Override
        public synchronized void despawnRuntime(UUID ownerId) {
            dismissCalledForOwner = true;
        }
    }
}

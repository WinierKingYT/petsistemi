package com.petsistemi.application;

import com.petsistemi.api.result.PetDisableResult;
import com.petsistemi.api.result.PetRemoveResult;
import com.petsistemi.definition.PetDefinitionRegistry;
import com.petsistemi.domain.PetAvailabilityState;
import com.petsistemi.domain.PetDefinition;
import com.petsistemi.domain.PetInstance;
import com.petsistemi.domain.PetSelection;
import com.petsistemi.persistence.PetRepository;
import com.petsistemi.persistence.PetSelectionRepository;
import com.petsistemi.persistence.PlayerPetProfileCache;
import com.petsistemi.runtime.ActivePetRegistry;
import com.petsistemi.runtime.PetEntityController;
import com.petsistemi.runtime.PetRuntimeCoordinator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DefaultPetServiceTransactionTest {

    private Map<UUID, PetInstance> dbPets;
    private Map<UUID, PetSelection> dbSelections;

    private PetRepository mockPetRepository;
    private PetSelectionRepository mockSelectionRepository;
    private PetDefinitionRegistry mockDefRegistry;
    private ActivePetRegistry activePetRegistry;
    private PetEntityController mockEntityController;
    private PetRuntimeCoordinator mockCoordinator;
    private PlayerPetProfileCache profileCache;
    private DefaultPetService service;

    private UUID ownerId;
    private UUID petId;

    @BeforeEach
    void setUp() {
        dbPets = new HashMap<>();
        dbSelections = new HashMap<>();

        ownerId = UUID.randomUUID();
        petId = UUID.randomUUID();

        PetInstance pet = new PetInstance(petId, ownerId, "wolf", "TestKurt", 1, 0, PetAvailabilityState.AVAILABLE, System.currentTimeMillis(), System.currentTimeMillis());
        dbPets.put(petId, pet);
        dbSelections.put(ownerId, new PetSelection(ownerId, petId, System.currentTimeMillis()));

        mockPetRepository = new PetRepository() {
            @Override public Optional<PetInstance> findById(UUID id) { return Optional.ofNullable(dbPets.get(id)); }
            @Override public java.util.List<PetInstance> findByOwner(UUID ownerId) { return dbPets.values().stream().filter(p -> p.ownerId().equals(ownerId)).toList(); }
            @Override public Optional<PetInstance> findActiveByOwner(UUID ownerId) { return Optional.empty(); }
            @Override public void insert(PetInstance pet) { dbPets.put(pet.petId(), pet); }
            @Override public void update(PetInstance pet) { dbPets.put(pet.petId(), pet); }
            @Override public void delete(UUID petId) { dbPets.remove(petId); }
            @Override public void setActivePet(UUID ownerId, UUID petId) {}
            @Override public void clearActivePet(UUID ownerId) {}
            @Override public void switchActivePet(UUID ownerId, UUID previousPetId, UUID newPetId) {}
            @Override public void clearActivePetAndSetAvailable(UUID ownerId, UUID petId) {}
            @Override public void restoreActivePet(UUID ownerId, UUID previousPetId, UUID failedPetId) {}

            @Override
            public void disablePetTransactional(UUID clearSelectionOwnerId, PetInstance updatedPet) {
                if (clearSelectionOwnerId != null) {
                    dbSelections.remove(clearSelectionOwnerId);
                }
                dbPets.put(updatedPet.petId(), updatedPet);
            }

            @Override
            public void removePetTransactional(UUID clearSelectionOwnerId, UUID targetPetId) {
                if (clearSelectionOwnerId != null) {
                    dbSelections.remove(clearSelectionOwnerId);
                }
                dbPets.remove(targetPetId);
            }
        };

        mockSelectionRepository = new PetSelectionRepository() {
            @Override public Optional<PetSelection> findByOwner(UUID ownerId) { return Optional.ofNullable(dbSelections.get(ownerId)); }
            @Override public void select(UUID ownerId, UUID petId) { dbSelections.put(ownerId, new PetSelection(ownerId, petId, System.currentTimeMillis())); }
            @Override public void clear(UUID ownerId) { dbSelections.remove(ownerId); }
            @Override public void switchSelection(UUID ownerId, UUID previousPetId, UUID newPetId) { dbSelections.put(ownerId, new PetSelection(ownerId, newPetId, System.currentTimeMillis())); }
        };

        mockDefRegistry = new PetDefinitionRegistry() {
            @Override public Optional<PetDefinition> find(String id) { return Optional.of(new PetDefinition("wolf", "Kurt", Collections.emptyList(), "WOLF", false, false, true, false, true, true, 100, true, Collections.emptyList())); }
            @Override public java.util.Collection<PetDefinition> getAll() { return Collections.emptyList(); }
            @Override public void reload() {}
        };

        activePetRegistry = new ActivePetRegistry();
        mockEntityController = new PetEntityController() {
            @Override public org.bukkit.entity.Entity spawn(PetInstance instance, PetDefinition def, org.bukkit.entity.Player owner) { return null; }
            @Override public void remove(org.bukkit.entity.Entity entity) {}
            @Override public void updateName(org.bukkit.entity.Entity entity, PetInstance instance, PetDefinition def) {}
            @Override public boolean isValid(org.bukkit.entity.Entity entity) { return false; }
        };

        mockCoordinator = new PetRuntimeCoordinator(null, mockDefRegistry, activePetRegistry, mockEntityController, null);
        com.petsistemi.persistence.DatabaseExecutor dbExecutor = new com.petsistemi.persistence.DatabaseExecutor(java.util.logging.Logger.getLogger("TxTest"));
        com.petsistemi.bootstrap.FakeMainThreadDispatcher dispatcher = new com.petsistemi.bootstrap.FakeMainThreadDispatcher();
        service = new DefaultPetService(null, mockPetRepository, mockSelectionRepository, mockDefRegistry, activePetRegistry, mockEntityController, dbExecutor, dispatcher, profileCache, null);
    }

    @Test
    void testDisablePetAtomicTransactionPreservesDisabledState() {
        PetDisableResult result = service.disablePet(petId);
        assertTrue(result.success(), "disablePet must succeed");

        PetInstance saved = mockPetRepository.findById(petId).orElseThrow();
        assertEquals(PetAvailabilityState.DISABLED, saved.availabilityState(), "Persistence state MUST remain DISABLED after service cleanup");
        assertTrue(dbSelections.isEmpty(), "Selection MUST be cleared inside transaction");
    }

    @Test
    void testRemovePetAtomicTransactionRemovesPetAndSelection() {
        PetRemoveResult result = service.removePet(petId);
        assertTrue(result.success(), "removePet must succeed");

        assertTrue(mockPetRepository.findById(petId).isEmpty(), "Pet record MUST be deleted from database");
        assertTrue(dbSelections.isEmpty(), "Selection MUST be cleared inside transaction");
    }
}

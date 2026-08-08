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
import com.petsistemi.runtime.ActivePetRegistry;
import com.petsistemi.runtime.PetRuntimeCoordinator;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PetEvolutionOperationTest {

    private DatabaseExecutor dbExecutor;

    @AfterEach
    void tearDown() {
        if (dbExecutor != null && !dbExecutor.isClosed()) dbExecutor.close();
    }

    @Test
    void persistentEvolutionPreservesIdentityAndProgress() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID petId = UUID.randomUUID();
        PetInstance original = new PetInstance(petId, ownerId, "wolf", "Bobi", 14, 987,
                PetAvailabilityState.AVAILABLE, 1234L, 1234L);
        AtomicReference<PetInstance> stored = new AtomicReference<>(original);
        PetRepository repository = repository(stored);
        PetSelectionRepository selection = emptySelection();
        PetDefinition wolf = PetDefinition.builder("wolf", "Kurt").build();
        PetDefinition phoenix = PetDefinition.builder("phoenix", "Anka").build();
        PetDefinitionRegistry definitions = definitions(wolf, phoenix);
        ActivePetRegistry activePets = new ActivePetRegistry();
        PetRuntimeCoordinator coordinator = new PetRuntimeCoordinator(null, definitions, activePets, null, null);
        dbExecutor = new DatabaseExecutor(Logger.getLogger("PetEvolutionOperationTest"));
        PetRuntimeOperationService service = new PetRuntimeOperationService(null, repository, selection,
                definitions, coordinator, null, dbExecutor, new FakeMainThreadDispatcher());
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(ownerId);
        when(player.isOnline()).thenReturn(true);

        var result = service.evolveAsync(player, petId, "PHOENIX").get(5, TimeUnit.SECONDS);

        assertTrue(result.success(), result.message());
        PetInstance evolved = stored.get();
        assertEquals("phoenix", evolved.definitionId());
        assertEquals(petId, evolved.petId());
        assertEquals(ownerId, evolved.ownerId());
        assertEquals("Bobi", evolved.customName());
        assertEquals(14, evolved.level());
        assertEquals(987, evolved.experience());
        assertEquals(1234L, evolved.createdAt());
        assertEquals("wolf", result.before().definitionId());
        assertEquals("phoenix", result.after().definitionId());
    }

    private static PetRepository repository(AtomicReference<PetInstance> stored) {
        return new PetRepository() {
            @Override public Optional<PetInstance> findById(UUID id) { return Optional.ofNullable(stored.get()).filter(p -> p.petId().equals(id)); }
            @Override public List<PetInstance> findByOwner(UUID ownerId) { return List.of(stored.get()); }
            @Override public Optional<PetInstance> findActiveByOwner(UUID ownerId) { return Optional.empty(); }
            @Override public void insert(PetInstance pet) { stored.set(pet); }
            @Override public void update(PetInstance pet) { stored.set(pet); }
            @Override public void delete(UUID petId) {}
            @Override public void setActivePet(UUID ownerId, UUID petId) {}
            @Override public void clearActivePet(UUID ownerId) {}
            @Override public void switchActivePet(UUID ownerId, UUID previousPetId, UUID newPetId) {}
            @Override public void clearActivePetAndSetAvailable(UUID ownerId, UUID petId) {}
            @Override public void restoreActivePet(UUID ownerId, UUID previousPetId, UUID failedPetId) {}
        };
    }

    private static PetSelectionRepository emptySelection() {
        return new PetSelectionRepository() {
            @Override public Optional<PetSelection> findByOwner(UUID ownerId) { return Optional.empty(); }
            @Override public void select(UUID ownerId, UUID petId) {}
            @Override public void clear(UUID ownerId) {}
            @Override public void switchSelection(UUID ownerId, UUID previousPetId, UUID newPetId) {}
            @Override public void updateFollowMode(UUID ownerId, com.petsistemi.domain.PetFollowMode followMode) {}
        };
    }

    private static PetDefinitionRegistry definitions(PetDefinition... definitions) {
        return new PetDefinitionRegistry() {
            @Override public Optional<PetDefinition> find(String id) {
                return List.of(definitions).stream().filter(def -> def.id().equalsIgnoreCase(id)).findFirst();
            }
            @Override public Collection<PetDefinition> getAll() { return List.of(definitions); }
            @Override public void reload() {}
        };
    }
}

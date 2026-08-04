package com.petsistemi.persistence;

import com.petsistemi.domain.PetAvailabilityState;
import com.petsistemi.domain.PetInstance;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class PetAtomicOperationTest {

    private Connection connection;
    private SqlitePetRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        connection = DriverManager.getConnection("jdbc:sqlite::memory:");
        SchemaMigrator.migrate(connection);

        ConnectionProvider testProvider = new ConnectionProvider() {
            @Override
            public Connection getConnection() {
                return connection;
            }

            @Override
            public void close() {}
        };

        repository = new SqlitePetRepository(testProvider, Logger.getLogger("TestLogger"));
    }

    @AfterEach
    void tearDown() throws Exception {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }

    @Test
    void testDisablePetTransactionalClearsSelectionAndUpdatesState() {
        UUID ownerId = UUID.randomUUID();
        UUID petId = UUID.randomUUID();
        PetInstance pet = new PetInstance(petId, ownerId, "wolf", "Kurt", 1, 0, PetAvailabilityState.AVAILABLE, 1000L, 1000L);

        repository.insert(pet);
        repository.setActivePet(ownerId, petId);

        assertTrue(repository.findActiveByOwner(ownerId).isPresent());

        PetInstance updated = pet.withAvailabilityState(PetAvailabilityState.DISABLED);
        repository.disablePetTransactional(ownerId, updated);

        // Selection should be cleared
        assertTrue(repository.findActiveByOwner(ownerId).isEmpty());

        // Pet state should be DISABLED
        Optional<PetInstance> disabledOpt = repository.findById(petId);
        assertTrue(disabledOpt.isPresent());
        assertEquals(PetAvailabilityState.DISABLED, disabledOpt.get().availabilityState());
    }

    @Test
    void testRemovePetTransactionalClearsSelectionAndDeletesPet() {
        UUID ownerId = UUID.randomUUID();
        UUID petId = UUID.randomUUID();
        PetInstance pet = new PetInstance(petId, ownerId, "cat", "Kedi", 1, 0, PetAvailabilityState.AVAILABLE, 1000L, 1000L);

        repository.insert(pet);
        repository.setActivePet(ownerId, petId);

        assertTrue(repository.findActiveByOwner(ownerId).isPresent());

        repository.removePetTransactional(ownerId, petId);

        // Selection should be cleared
        assertTrue(repository.findActiveByOwner(ownerId).isEmpty());

        // Pet record should be deleted
        assertTrue(repository.findById(petId).isEmpty());
    }

    @Test
    void testDisableNonExistentPetRollsBack() {
        UUID ownerId = UUID.randomUUID();
        UUID nonExistentPetId = UUID.randomUUID();
        PetInstance dummy = new PetInstance(nonExistentPetId, ownerId, "wolf", "Ghost", 1, 0, PetAvailabilityState.DISABLED, 1000L, 1000L);

        assertThrows(PetPersistenceException.class, () -> 
            repository.disablePetTransactional(ownerId, dummy)
        );
    }
}

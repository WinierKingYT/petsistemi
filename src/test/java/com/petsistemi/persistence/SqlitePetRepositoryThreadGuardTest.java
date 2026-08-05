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
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the REAL SqlitePetRepository/SqlitePetSelectionRepository enforce the
 * database-thread boundary (no fake repository that hides missing guards).
 */
class SqlitePetRepositoryThreadGuardTest {

    private Connection connection;
    private DatabaseExecutor dbExecutor;
    private SqlitePetRepository petRepository;
    private SqlitePetSelectionRepository selectionRepository;

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

        // Creating the DatabaseExecutor arms the global thread guard.
        dbExecutor = new DatabaseExecutor(Logger.getLogger("SqlitePetRepositoryThreadGuardTest"));
        petRepository = new SqlitePetRepository(testProvider, Logger.getLogger("TestLogger"));
        selectionRepository = new SqlitePetSelectionRepository(testProvider, Logger.getLogger("TestLogger"));
    }

    @AfterEach
    void tearDown() throws Exception {
        if (dbExecutor != null && !dbExecutor.isClosed()) {
            dbExecutor.close();
        }
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }

    @Test
    void testPetRepositoryOffThreadCallsThrowGuardException() {
        UUID petId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();

        assertThrows(IllegalStateException.class, () -> petRepository.findById(petId));
        assertThrows(IllegalStateException.class, () -> petRepository.findByOwner(ownerId));
        assertThrows(IllegalStateException.class, () -> petRepository.insert(new PetInstance(petId, ownerId, "wolf", "Bobi", 1, 0, PetAvailabilityState.AVAILABLE, 1L, 1L)));
        assertThrows(IllegalStateException.class, () -> petRepository.update(new PetInstance(petId, ownerId, "wolf", "Bobi", 1, 0, PetAvailabilityState.AVAILABLE, 1L, 1L)));
        assertThrows(IllegalStateException.class, () -> petRepository.delete(petId));
        assertThrows(IllegalStateException.class, () -> petRepository.setActivePet(ownerId, petId));
        assertThrows(IllegalStateException.class, () -> petRepository.clearActivePet(ownerId));
        assertThrows(IllegalStateException.class, () -> petRepository.switchActivePet(ownerId, null, petId));
        assertThrows(IllegalStateException.class, () -> petRepository.restoreActivePet(ownerId, null, petId));
        assertThrows(IllegalStateException.class, () -> petRepository.disablePetTransactional(ownerId, new PetInstance(petId, ownerId, "wolf", "Bobi", 1, 0, PetAvailabilityState.DISABLED, 1L, 1L)));
        assertThrows(IllegalStateException.class, () -> petRepository.removePetTransactional(ownerId, petId));
    }

    @Test
    void testSelectionRepositoryOffThreadCallsThrowGuardException() {
        UUID ownerId = UUID.randomUUID();
        UUID petId = UUID.randomUUID();

        assertThrows(IllegalStateException.class, () -> selectionRepository.findByOwner(ownerId));
        assertThrows(IllegalStateException.class, () -> selectionRepository.select(ownerId, petId));
        assertThrows(IllegalStateException.class, () -> selectionRepository.clear(ownerId));
        assertThrows(IllegalStateException.class, () -> selectionRepository.switchSelection(ownerId, null, petId));
    }

    @Test
    void testPetRepositoryOnDatabaseThreadSucceeds() throws Exception {
        UUID petId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();

        dbExecutor.submit(() -> {
            petRepository.insert(new PetInstance(petId, ownerId, "wolf", "Bobi", 1, 0, PetAvailabilityState.AVAILABLE, 1L, 1L));
            return null;
        }).get(5, TimeUnit.SECONDS);

        Optional<PetInstance> found = dbExecutor.submit(() -> petRepository.findById(petId)).get(5, TimeUnit.SECONDS);
        assertTrue(found.isPresent());
        assertEquals("Bobi", found.get().customName());
    }

    @Test
    void testSelectionRepositoryOnDatabaseThreadSucceeds() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID petId = UUID.randomUUID();

        dbExecutor.submit(() -> {
            petRepository.insert(new PetInstance(petId, ownerId, "wolf", "Bobi", 1, 0, PetAvailabilityState.AVAILABLE, 1L, 1L));
            selectionRepository.select(ownerId, petId);
            return null;
        }).get(5, TimeUnit.SECONDS);

        Boolean present = dbExecutor.submit(() -> selectionRepository.findByOwner(ownerId).isPresent()).get(5, TimeUnit.SECONDS);
        assertTrue(present);
    }
}

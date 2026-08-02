package com.petsistemi.persistence;

import com.petsistemi.domain.PetInstance;
import com.petsistemi.domain.PetStorageState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class SqlitePetRepositoryTest {

    private Connection connection;
    private SqlitePetRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        connection = DriverManager.getConnection("jdbc:sqlite::memory:");
        
        try (Statement st = connection.createStatement()) {
            st.execute("PRAGMA foreign_keys = ON;");
            st.execute("CREATE TABLE IF NOT EXISTS pets (" +
                    "pet_id TEXT PRIMARY KEY, " +
                    "owner_id TEXT NOT NULL, " +
                    "definition_id TEXT NOT NULL, " +
                    "custom_name TEXT, " +
                    "level INTEGER NOT NULL DEFAULT 1, " +
                    "experience INTEGER NOT NULL DEFAULT 0, " +
                    "state TEXT NOT NULL DEFAULT 'AVAILABLE', " +
                    "created_at INTEGER NOT NULL, " +
                    "updated_at INTEGER NOT NULL" +
                    ");");

            st.execute("CREATE TABLE IF NOT EXISTS player_active_pets (" +
                    "owner_id TEXT PRIMARY KEY, " +
                    "pet_id TEXT NOT NULL UNIQUE, " +
                    "updated_at INTEGER NOT NULL, " +
                    "FOREIGN KEY (pet_id) REFERENCES pets(pet_id) ON DELETE CASCADE" +
                    ");");
        }

        DatabaseManager mockManager = new DatabaseManager(null) {
            @Override
            public Connection getConnection() {
                return connection;
            }
        };

        repository = new SqlitePetRepository(mockManager, Logger.getLogger("TestLogger"));
    }

    @AfterEach
    void tearDown() throws Exception {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }

    @Test
    void testInsertAndFindById() {
        UUID petId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();

        PetInstance pet = new PetInstance(petId, ownerId, "wolf", "Bobi", 1, 0, PetStorageState.AVAILABLE, 1000L, 1000L);
        repository.insert(pet);

        Optional<PetInstance> found = repository.findById(petId);
        assertTrue(found.isPresent());
        assertEquals("Bobi", found.get().customName());
    }

    @Test
    void testSwitchActivePetTransaction() {
        UUID ownerId = UUID.randomUUID();
        UUID petA = UUID.randomUUID();
        UUID petB = UUID.randomUUID();

        repository.insert(new PetInstance(petA, ownerId, "wolf", "PetA", 1, 0, PetStorageState.AVAILABLE, 1000L, 1000L));
        repository.insert(new PetInstance(petB, ownerId, "cat", "PetB", 1, 0, PetStorageState.AVAILABLE, 1000L, 1000L));

        // 1. Switch to Pet A
        repository.switchActivePet(ownerId, null, petA);

        Optional<PetInstance> activeOpt = repository.findActiveByOwner(ownerId);
        assertTrue(activeOpt.isPresent());
        assertEquals(petA, activeOpt.get().petId());
        assertEquals(PetStorageState.ACTIVE, repository.findById(petA).get().storageState());

        // 2. Switch to Pet B
        repository.switchActivePet(ownerId, petA, petB);

        activeOpt = repository.findActiveByOwner(ownerId);
        assertTrue(activeOpt.isPresent());
        assertEquals(petB, activeOpt.get().petId());
        assertEquals(PetStorageState.AVAILABLE, repository.findById(petA).get().storageState());
        assertEquals(PetStorageState.ACTIVE, repository.findById(petB).get().storageState());
    }

    @Test
    void testRestoreActivePetTransaction() {
        UUID ownerId = UUID.randomUUID();
        UUID petA = UUID.randomUUID();
        UUID petB = UUID.randomUUID();

        repository.insert(new PetInstance(petA, ownerId, "wolf", "PetA", 1, 0, PetStorageState.AVAILABLE, 1000L, 1000L));
        repository.insert(new PetInstance(petB, ownerId, "cat", "PetB", 1, 0, PetStorageState.AVAILABLE, 1000L, 1000L));

        // Activate Pet A
        repository.switchActivePet(ownerId, null, petA);

        // Attempting Pet B spawn fails, calling restoreActivePet
        repository.restoreActivePet(ownerId, petA, petB);

        Optional<PetInstance> activeOpt = repository.findActiveByOwner(ownerId);
        assertTrue(activeOpt.isPresent());
        assertEquals(petA, activeOpt.get().petId());
        assertEquals(PetStorageState.ACTIVE, repository.findById(petA).get().storageState());
        assertEquals(PetStorageState.AVAILABLE, repository.findById(petB).get().storageState());
    }

    @Test
    void testClearActivePetAndSetAvailableTransaction() {
        UUID ownerId = UUID.randomUUID();
        UUID petA = UUID.randomUUID();

        repository.insert(new PetInstance(petA, ownerId, "wolf", "PetA", 1, 0, PetStorageState.AVAILABLE, 1000L, 1000L));
        repository.switchActivePet(ownerId, null, petA);

        repository.clearActivePetAndSetAvailable(ownerId, petA);

        assertTrue(repository.findActiveByOwner(ownerId).isEmpty());
        assertEquals(PetStorageState.AVAILABLE, repository.findById(petA).get().storageState());
    }
}

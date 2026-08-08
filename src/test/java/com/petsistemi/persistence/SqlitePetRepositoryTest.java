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

class SqlitePetRepositoryTest {

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
    void testInsertAndFindById() {
        UUID petId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();

        PetInstance pet = new PetInstance(petId, ownerId, "wolf", "Bobi", 1, 0, PetAvailabilityState.AVAILABLE, 1000L, 1000L);
        repository.insert(pet);

        Optional<PetInstance> found = repository.findById(petId);
        assertTrue(found.isPresent());
        assertEquals("Bobi", found.get().customName());
        assertEquals(PetAvailabilityState.AVAILABLE, found.get().availabilityState());
    }

    @Test
    void updatePersistsDefinitionChangeWithoutLosingProgress() {
        UUID petId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        PetInstance original = new PetInstance(petId, ownerId, "wolf", "Bobi", 12, 345,
                PetAvailabilityState.AVAILABLE, 1000L, 1000L);
        repository.insert(original);

        repository.update(original.withDefinitionId("phoenix"));

        PetInstance evolved = repository.findById(petId).orElseThrow();
        assertEquals("phoenix", evolved.definitionId());
        assertEquals("Bobi", evolved.customName());
        assertEquals(12, evolved.level());
        assertEquals(345, evolved.experience());
        assertEquals(1000L, evolved.createdAt());
    }

    @Test
    void testSwitchActivePetTransaction() {
        UUID ownerId = UUID.randomUUID();
        UUID petA = UUID.randomUUID();
        UUID petB = UUID.randomUUID();

        repository.insert(new PetInstance(petA, ownerId, "wolf", "PetA", 1, 0, PetAvailabilityState.AVAILABLE, 1000L, 1000L));
        repository.insert(new PetInstance(petB, ownerId, "cat", "PetB", 1, 0, PetAvailabilityState.AVAILABLE, 1000L, 1000L));

        repository.switchActivePet(ownerId, null, petA);

        Optional<PetInstance> activeOpt = repository.findActiveByOwner(ownerId);
        assertTrue(activeOpt.isPresent());
        assertEquals(petA, activeOpt.get().petId());

        repository.switchActivePet(ownerId, petA, petB);

        activeOpt = repository.findActiveByOwner(ownerId);
        assertTrue(activeOpt.isPresent());
        assertEquals(petB, activeOpt.get().petId());
    }

    @Test
    void testRestoreActivePetTransaction() {
        UUID ownerId = UUID.randomUUID();
        UUID petA = UUID.randomUUID();
        UUID petB = UUID.randomUUID();

        repository.insert(new PetInstance(petA, ownerId, "wolf", "PetA", 1, 0, PetAvailabilityState.AVAILABLE, 1000L, 1000L));
        repository.insert(new PetInstance(petB, ownerId, "cat", "PetB", 1, 0, PetAvailabilityState.AVAILABLE, 1000L, 1000L));

        repository.switchActivePet(ownerId, null, petA);
        repository.switchActivePet(ownerId, petA, petB);

        repository.restoreActivePet(ownerId, petA, petB);

        Optional<PetInstance> activeOpt = repository.findActiveByOwner(ownerId);
        assertTrue(activeOpt.isPresent());
        assertEquals(petA, activeOpt.get().petId());
    }

    @Test
    void testSwitchActivePetRollbackOnConstraintViolation() {
        UUID ownerId = UUID.randomUUID();
        UUID nonExistentPetId = UUID.randomUUID();

        assertThrows(PetPersistenceException.class, () -> 
            repository.switchActivePet(ownerId, null, nonExistentPetId)
        );

        assertTrue(repository.findActiveByOwner(ownerId).isEmpty());
    }

    @Test
    void testUniquePetIdConstraintAcrossDifferentOwners() {
        UUID owner1 = UUID.randomUUID();
        UUID owner2 = UUID.randomUUID();
        UUID sharedPet = UUID.randomUUID();

        repository.insert(new PetInstance(sharedPet, owner1, "wolf", "Pet1", 1, 0, PetAvailabilityState.AVAILABLE, 1000L, 1000L));
        repository.switchActivePet(owner1, null, sharedPet);

        // Attempting to assign same active pet_id to owner2 must fail due to UNIQUE constraint
        assertThrows(PetPersistenceException.class, () -> 
            repository.switchActivePet(owner2, null, sharedPet)
        );

        // Verify owner1 still owns active pet and owner2 has no active pet
        assertEquals(sharedPet, repository.findActiveByOwner(owner1).get().petId());
        assertTrue(repository.findActiveByOwner(owner2).isEmpty());
    }

    @Test
    void testClearActivePetAndSetAvailableTransaction() {
        UUID ownerId = UUID.randomUUID();
        UUID petA = UUID.randomUUID();

        repository.insert(new PetInstance(petA, ownerId, "wolf", "PetA", 1, 0, PetAvailabilityState.AVAILABLE, 1000L, 1000L));
        repository.switchActivePet(ownerId, null, petA);

        repository.clearActivePetAndSetAvailable(ownerId, petA);

        assertTrue(repository.findActiveByOwner(ownerId).isEmpty());
    }
}

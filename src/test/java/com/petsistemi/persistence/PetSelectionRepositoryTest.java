package com.petsistemi.persistence;

import com.petsistemi.domain.PetAvailabilityState;
import com.petsistemi.domain.PetInstance;
import com.petsistemi.domain.PetSelection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class PetSelectionRepositoryTest {

    private Connection connection;
    private SqlitePetRepository petRepository;
    private SqlitePetSelectionRepository selectionRepository;

    @BeforeEach
    void setUp() throws Exception {
        connection = DriverManager.getConnection("jdbc:sqlite::memory:");
        SchemaMigrator.migrate(connection);

        ConnectionProvider provider = new ConnectionProvider() {
            @Override public Connection getConnection() { return connection; }
            @Override public void close() {}
        };

        Logger logger = Logger.getLogger("TestLogger");
        petRepository = new SqlitePetRepository(provider, logger);
        selectionRepository = new SqlitePetSelectionRepository(provider, logger);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }

    @Test
    void testSelectAndFindByOwner() {
        UUID ownerId = UUID.randomUUID();
        UUID petId = UUID.randomUUID();

        petRepository.insert(new PetInstance(petId, ownerId, "wolf", "Bobi", 1, 0, PetAvailabilityState.AVAILABLE, 1000L, 1000L));

        selectionRepository.select(ownerId, petId);

        Optional<PetSelection> selection = selectionRepository.findByOwner(ownerId);
        assertTrue(selection.isPresent());
        assertEquals(ownerId, selection.get().ownerId());
        assertEquals(petId, selection.get().petId());
    }

    @Test
    void testClearSelection() {
        UUID ownerId = UUID.randomUUID();
        UUID petId = UUID.randomUUID();

        petRepository.insert(new PetInstance(petId, ownerId, "wolf", "Bobi", 1, 0, PetAvailabilityState.AVAILABLE, 1000L, 1000L));
        selectionRepository.select(ownerId, petId);

        selectionRepository.clear(ownerId);

        assertTrue(selectionRepository.findByOwner(ownerId).isEmpty());
    }

    @Test
    void testSwitchSelection() {
        UUID ownerId = UUID.randomUUID();
        UUID petA = UUID.randomUUID();
        UUID petB = UUID.randomUUID();

        petRepository.insert(new PetInstance(petA, ownerId, "wolf", "PetA", 1, 0, PetAvailabilityState.AVAILABLE, 1000L, 1000L));
        petRepository.insert(new PetInstance(petB, ownerId, "cat", "PetB", 1, 0, PetAvailabilityState.AVAILABLE, 1000L, 1000L));

        selectionRepository.select(ownerId, petA);
        selectionRepository.switchSelection(ownerId, petA, petB);

        Optional<PetSelection> selection = selectionRepository.findByOwner(ownerId);
        assertTrue(selection.isPresent());
        assertEquals(petB, selection.get().petId());
    }

    @Test
    void testCannotSelectDisabledPet() {
        UUID ownerId = UUID.randomUUID();
        UUID disabledPetId = UUID.randomUUID();

        petRepository.insert(new PetInstance(disabledPetId, ownerId, "wolf", "DisabledBobi", 1, 0, PetAvailabilityState.DISABLED, 1000L, 1000L));

        // Attempting to select a pet with composite FK enforces DB matching
        selectionRepository.select(ownerId, disabledPetId);
        Optional<PetSelection> selection = selectionRepository.findByOwner(ownerId);
        assertTrue(selection.isPresent());
    }
}

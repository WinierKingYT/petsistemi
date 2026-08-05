package com.petsistemi.persistence;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class ThreadBoundaryTest {

    private DatabaseExecutor dbExecutor;
    private PetRepository repository;

    @BeforeEach
    void setUp() {
        dbExecutor = new DatabaseExecutor(Logger.getLogger("ThreadBoundaryTest"));
        repository = new PetRepository() {
            @Override
            public Optional<com.petsistemi.domain.PetInstance> findById(UUID petId) {
                DatabaseThreadGuard.requireDatabaseThread();
                return Optional.empty();
            }
            @Override public java.util.List<com.petsistemi.domain.PetInstance> findByOwner(UUID ownerId) { DatabaseThreadGuard.requireDatabaseThread(); return java.util.List.of(); }
            @Override public Optional<com.petsistemi.domain.PetInstance> findActiveByOwner(UUID ownerId) { DatabaseThreadGuard.requireDatabaseThread(); return Optional.empty(); }
            @Override public void insert(com.petsistemi.domain.PetInstance pet) { DatabaseThreadGuard.requireDatabaseThread(); }
            @Override public void update(com.petsistemi.domain.PetInstance pet) { DatabaseThreadGuard.requireDatabaseThread(); }
            @Override public void delete(UUID petId) { DatabaseThreadGuard.requireDatabaseThread(); }
            @Override public void setActivePet(UUID ownerId, UUID petId) { DatabaseThreadGuard.requireDatabaseThread(); }
            @Override public void clearActivePet(UUID ownerId) { DatabaseThreadGuard.requireDatabaseThread(); }
            @Override public void switchActivePet(UUID ownerId, UUID previousPetId, UUID newPetId) { DatabaseThreadGuard.requireDatabaseThread(); }
            @Override public void clearActivePetAndSetAvailable(UUID ownerId, UUID petId) { DatabaseThreadGuard.requireDatabaseThread(); }
            @Override public void restoreActivePet(UUID ownerId, UUID previousPetId, UUID failedPetId) { DatabaseThreadGuard.requireDatabaseThread(); }
        };
    }

    @AfterEach
    void tearDown() {
        if (dbExecutor != null && !dbExecutor.isClosed()) {
            dbExecutor.close();
        }
    }

    @Test
    void testDirectMainThreadRepositoryAccessThrowsException() {
        UUID randomId = UUID.randomUUID();
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> {
            repository.findById(randomId);
        });
        assertTrue(ex.getMessage().contains("PetSistemi-Database thread'inde çalıştırılabilir"));
    }

    @Test
    void testDatabaseThreadAccessSucceeds() throws Exception {
        UUID randomId = UUID.randomUUID();
        Optional<com.petsistemi.domain.PetInstance> res = dbExecutor.submit(() -> repository.findById(randomId)).get();
        assertTrue(res.isEmpty());
    }
}

package com.petsistemi.application;

import com.petsistemi.bootstrap.FakeMainThreadDispatcher;
import com.petsistemi.definition.PetDefinitionRegistry;
import com.petsistemi.domain.PetAvailabilityState;
import com.petsistemi.domain.PetDefinition;
import com.petsistemi.domain.PetFollowMode;
import com.petsistemi.domain.PetInstance;
import com.petsistemi.domain.PetRuntimeState;
import com.petsistemi.domain.RuntimeRepresentationType;
import com.petsistemi.persistence.ConnectionProvider;
import com.petsistemi.persistence.DatabaseExecutor;
import com.petsistemi.persistence.DatabaseThreadGuard;
import com.petsistemi.persistence.SchemaMigrator;
import com.petsistemi.persistence.SqlitePetRepository;
import com.petsistemi.persistence.SqlitePetSelectionRepository;
import com.petsistemi.runtime.ActivePet;
import com.petsistemi.runtime.ActivePetRegistry;
import com.petsistemi.runtime.PetRepresentationController;
import com.petsistemi.runtime.PetRepresentationRegistry;
import com.petsistemi.runtime.PetRuntimeCoordinator;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FollowModeOperationTest {

    private Connection connection;
    private DatabaseExecutor dbExecutor;
    private FakeMainThreadDispatcher mainThreadDispatcher;
    private SqlitePetRepository petRepository;
    private SqlitePetSelectionRepository selectionRepository;
    private PetRuntimeCoordinator coordinator;
    private ActivePetRegistry activeRegistry;
    private PetRuntimeOperationService service;

    private final UUID ownerId = UUID.randomUUID();
    private final UUID petId = UUID.randomUUID();
    private Entity spawnedEntity;

    @BeforeEach
    void setUp() throws Exception {
        connection = DriverManager.getConnection("jdbc:sqlite::memory:");
        SchemaMigrator.migrate(connection);
        ConnectionProvider provider = new ConnectionProvider() {
            @Override public Connection getConnection() { return connection; }
            @Override public void close() {}
        };
        Logger logger = Logger.getLogger("FollowModeOperationTest");
        petRepository = new SqlitePetRepository(provider, logger);
        selectionRepository = new SqlitePetSelectionRepository(provider, logger);

        dbExecutor = new DatabaseExecutor(logger);
        DatabaseThreadGuard.setGuardEnabled(false);
        mainThreadDispatcher = new FakeMainThreadDispatcher();
        activeRegistry = new ActivePetRegistry();

        PetDefinition def = new PetDefinition("wolf", "Kurt", List.of(), "DOG", false, false, false, false, true, true, 100, true, List.of());
        PetDefinitionRegistry defRegistry = new PetDefinitionRegistry() {
            @Override public Optional<PetDefinition> find(String id) { return Optional.of(def); }
            @Override public Collection<PetDefinition> getAll() { return List.of(def); }
            @Override public void reload() {}
        };

        spawnedEntity = mock(Entity.class);
        when(spawnedEntity.getUniqueId()).thenReturn(UUID.randomUUID());
        when(spawnedEntity.isValid()).thenReturn(true);

        PetRepresentationRegistry representationRegistry = new PetRepresentationRegistry();
        representationRegistry.register(RuntimeRepresentationType.ENTITY, new PetRepresentationController() {
            @Override public Entity spawn(PetInstance pet, PetDefinition definition, Player owner) { return spawnedEntity; }
            @Override public void updateVisual(Entity primaryEntity, PetInstance pet, PetDefinition definition) {}
            @Override public void remove(Entity primaryEntity) {}
            @Override public boolean isValid(Entity primaryEntity) { return true; }
        });

        coordinator = new PetRuntimeCoordinator(null, defRegistry, activeRegistry, null, null, representationRegistry, null);
        service = new PetRuntimeOperationService(null, petRepository, selectionRepository, defRegistry, coordinator, null, dbExecutor, mainThreadDispatcher);
    }

    @AfterEach
    void tearDown() throws Exception {
        DatabaseThreadGuard.setGuardEnabled(true);
        if (dbExecutor != null && !dbExecutor.isClosed()) {
            dbExecutor.close();
        }
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }

    private Player player() {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(ownerId);
        when(player.isOnline()).thenReturn(true);
        return player;
    }

    private void insertAndSelectPet() {
        petRepository.insert(new PetInstance(petId, ownerId, "wolf", "Kurt", 1, 0, PetAvailabilityState.AVAILABLE, 1000L, 1000L));
        selectionRepository.select(ownerId, petId);
    }

    @Test
    void setFollowModePersistsAndAppliesToActivePet() throws Exception {
        insertAndSelectPet();
        ActivePet active = new ActivePet(petId, ownerId, UUID.randomUUID(), spawnedEntity, PetRuntimeState.ACTIVE);
        activeRegistry.register(active);

        Boolean result = service.setFollowModeAsync(player(), PetFollowMode.STAY).get(5, TimeUnit.SECONDS);

        assertTrue(result);
        assertEquals(PetFollowMode.STAY, active.getFollowMode(), "active runtime pet must switch mode");
        assertEquals(PetFollowMode.STAY, selectionRepository.findByOwner(ownerId).get().followMode(), "mode must be persisted");
    }

    @Test
    void setFollowModePersistFailureLeavesRuntimeUnchanged() throws Exception {
        insertAndSelectPet();
        ActivePet active = new ActivePet(petId, ownerId, UUID.randomUUID(), spawnedEntity, PetRuntimeState.ACTIVE);
        activeRegistry.register(active);
        service = new PetRuntimeOperationService(null, petRepository, new FailingFollowModeRepository(), null, coordinator, null, dbExecutor, mainThreadDispatcher);

        Boolean result = service.setFollowModeAsync(player(), PetFollowMode.WANDER).get(5, TimeUnit.SECONDS);

        assertFalse(result);
        assertEquals(PetFollowMode.FOLLOW, active.getFollowMode(), "mode must not change when persistence fails");
    }

    @Test
    void summonAppliesPersistedFollowMode() throws Exception {
        insertAndSelectPet();
        selectionRepository.updateFollowMode(ownerId, PetFollowMode.STAY);

        var result = service.summonAsync(player(), petId).get(5, TimeUnit.SECONDS);

        assertTrue(result.success(), result.message());
        Optional<ActivePet> activeOpt = activeRegistry.getByOwner(ownerId);
        assertTrue(activeOpt.isPresent());
        assertEquals(PetFollowMode.STAY, activeOpt.get().getFollowMode());
    }

    @Test
    void summonDefaultsToFollowWhenNoSelection() throws Exception {
        petRepository.insert(new PetInstance(petId, ownerId, "wolf", "Kurt", 1, 0, PetAvailabilityState.AVAILABLE, 1000L, 1000L));

        var result = service.summonAsync(player(), petId).get(5, TimeUnit.SECONDS);

        assertTrue(result.success(), result.message());
        Optional<ActivePet> activeOpt = activeRegistry.getByOwner(ownerId);
        assertTrue(activeOpt.isPresent());
        assertEquals(PetFollowMode.FOLLOW, activeOpt.get().getFollowMode());
    }

    /** Selection repo whose updateFollowMode always fails, simulating a DB error. */
    private static class FailingFollowModeRepository extends SqlitePetSelectionRepository {
        FailingFollowModeRepository() {
            super(new ConnectionProvider() {
                @Override public Connection getConnection() { throw new IllegalStateException("should not be reached"); }
                @Override public void close() {}
            }, Logger.getLogger("FailingFollowModeRepository"));
        }

        @Override
        public synchronized void updateFollowMode(UUID ownerId, PetFollowMode followMode) {
            throw new IllegalStateException("Simulated persistence failure");
        }
    }
}

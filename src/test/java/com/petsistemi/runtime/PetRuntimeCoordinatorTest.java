package com.petsistemi.runtime;

import com.petsistemi.definition.PetDefinitionRegistry;
import com.petsistemi.domain.PetAvailabilityState;
import com.petsistemi.domain.PetDefinition;
import com.petsistemi.domain.PetInstance;
import com.petsistemi.domain.PetRuntimeState;
import com.petsistemi.persistence.PetRepository;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PetRuntimeCoordinatorTest {

    private ActivePetRegistry activeRegistry;
    private PetRuntimeCoordinator coordinator;

    private int newPetSpawnCalls;
    private int restorePetSpawnCalls;
    private int newPetBehaviorInitCalls;
    private int restorePetBehaviorInitCalls;
    private int databaseSwitchCalls;
    private int databaseRestoreCalls;

    @BeforeEach
    void setUp() {
        activeRegistry = new ActivePetRegistry();
        newPetSpawnCalls = 0;
        restorePetSpawnCalls = 0;
        newPetBehaviorInitCalls = 0;
        restorePetBehaviorInitCalls = 0;
        databaseSwitchCalls = 0;
        databaseRestoreCalls = 0;
    }

    @Test
    void testNullOwnerThrowsIllegalArgumentException() {
        PetRepository mockRepository = createMockRepository(UUID.randomUUID(), UUID.randomUUID());
        PetDefinitionRegistry mockDefRegistry = createMockDefRegistry(true);
        PetEntityController mockEntityController = createMockEntityController(UUID.randomUUID(), false);
        PetBehaviorController mockBehaviorController = createMockBehaviorController(false);

        coordinator = new PetRuntimeCoordinator(null, mockRepository, mockDefRegistry, activeRegistry, mockEntityController, mockBehaviorController);
        PetInstance instanceB = new PetInstance(UUID.randomUUID(), UUID.randomUUID(), "cat", "PetB", 1, 0, PetAvailabilityState.AVAILABLE, 0, 0);
        PetDefinition defB = mockDefRegistry.find("wolf").get();

        assertThrows(IllegalArgumentException.class, () -> coordinator.spawnAndRegister(null, instanceB, defB));
    }

    @Test
    void testSpawnFailureRestoresPreviousPhysicalPetAndRegistry() {
        UUID ownerId = UUID.randomUUID();
        UUID petA = UUID.randomUUID();
        UUID petB = UUID.randomUUID();

        ActivePet activeA = new ActivePet(petA, ownerId, UUID.randomUUID(), null, PetRuntimeState.ACTIVE);
        activeRegistry.register(activeA);

        PetRepository mockRepository = createMockRepository(petA, ownerId);
        PetDefinitionRegistry mockDefRegistry = createMockDefRegistry(true);
        PetEntityController mockEntityController = createMockEntityController(petB, false); // Fails on petB spawn
        PetBehaviorController mockBehaviorController = createMockBehaviorController(false);

        Player mockPlayer = createMockPlayer(ownerId);
        coordinator = new PetRuntimeCoordinator(null, mockRepository, mockDefRegistry, activeRegistry, mockEntityController, mockBehaviorController);

        PetInstance instanceB = new PetInstance(petB, ownerId, "cat", "PetB", 1, 0, PetAvailabilityState.AVAILABLE, 0, 0);
        PetDefinition defB = mockDefRegistry.find("wolf").get();

        assertThrows(RuntimeException.class, () -> coordinator.spawnAndRegister(mockPlayer, instanceB, defB));

        assertEquals(1, newPetSpawnCalls);
        assertEquals(0, newPetBehaviorInitCalls);
        assertEquals(0, databaseSwitchCalls);
        assertEquals(1, restorePetSpawnCalls);
        assertEquals(1, restorePetBehaviorInitCalls);

        assertTrue(activeRegistry.getByOwner(ownerId).isPresent(), "Pet A must be re-registered in activeRegistry");
        assertEquals(petA, activeRegistry.getByOwner(ownerId).get().getPetId());
    }

    @Test
    void testBehaviorInitFailureRestoresPreviousPhysicalPetAndRegistry() {
        UUID ownerId = UUID.randomUUID();
        UUID petA = UUID.randomUUID();
        UUID petB = UUID.randomUUID();

        ActivePet activeA = new ActivePet(petA, ownerId, UUID.randomUUID(), null, PetRuntimeState.ACTIVE);
        activeRegistry.register(activeA);

        PetRepository mockRepository = createMockRepository(petA, ownerId);
        PetDefinitionRegistry mockDefRegistry = createMockDefRegistry(true);
        PetEntityController mockEntityController = createMockEntityController(UUID.randomUUID(), false);
        PetBehaviorController mockBehaviorController = createMockBehaviorController(true); // Fails on behavior init for Pet B

        Player mockPlayer = createMockPlayer(ownerId);
        coordinator = new PetRuntimeCoordinator(null, mockRepository, mockDefRegistry, activeRegistry, mockEntityController, mockBehaviorController);

        PetInstance instanceB = new PetInstance(petB, ownerId, "cat", "PetB", 1, 0, PetAvailabilityState.AVAILABLE, 0, 0);
        PetDefinition defB = mockDefRegistry.find("wolf").get();

        assertThrows(RuntimeException.class, () -> coordinator.spawnAndRegister(mockPlayer, instanceB, defB));

        assertEquals(1, newPetSpawnCalls);
        assertEquals(1, newPetBehaviorInitCalls);
        assertEquals(0, databaseSwitchCalls);
        assertEquals(1, restorePetSpawnCalls);
        assertEquals(1, restorePetBehaviorInitCalls);

        assertTrue(activeRegistry.getByOwner(ownerId).isPresent(), "Pet A must be re-registered in activeRegistry");
        assertEquals(petA, activeRegistry.getByOwner(ownerId).get().getPetId());
    }

    @Test
    void testDatabaseSwitchFailureRestoresPreviousPhysicalPetAndRegistry() {
        UUID ownerId = UUID.randomUUID();
        UUID petA = UUID.randomUUID();
        UUID petB = UUID.randomUUID();

        ActivePet activeA = new ActivePet(petA, ownerId, UUID.randomUUID(), null, PetRuntimeState.ACTIVE);
        activeRegistry.register(activeA);

        PetRepository mockRepository = new PetRepository() {
            @Override public Optional<PetInstance> findById(UUID id) { return Optional.of(new PetInstance(petA, ownerId, "wolf", "PetA", 1, 0, PetAvailabilityState.AVAILABLE, 0, 0)); }
            @Override public java.util.List<PetInstance> findByOwner(UUID ownerId) { return Collections.emptyList(); }
            @Override public Optional<PetInstance> findActiveByOwner(UUID ownerId) { return Optional.of(new PetInstance(petA, ownerId, "wolf", "PetA", 1, 0, PetAvailabilityState.AVAILABLE, 0, 0)); }
            @Override public void insert(PetInstance pet) {}
            @Override public void update(PetInstance pet) {}
            @Override public void delete(UUID petId) {}
            @Override public void setActivePet(UUID ownerId, UUID petId) {}
            @Override public void clearActivePet(UUID ownerId) {}
            @Override public void switchActivePet(UUID ownerId, UUID previousPetId, UUID newPetId) {
                databaseSwitchCalls++;
                throw new RuntimeException("Simulated DB Switch Failure");
            }
            @Override public void clearActivePetAndSetAvailable(UUID ownerId, UUID petId) {}
            @Override public void restoreActivePet(UUID ownerId, UUID previousPetId, UUID failedPetId) { databaseRestoreCalls++; }
        };

        PetDefinitionRegistry mockDefRegistry = createMockDefRegistry(true);
        PetEntityController mockEntityController = createMockEntityController(UUID.randomUUID(), false);
        PetBehaviorController mockBehaviorController = createMockBehaviorController(false);

        Player mockPlayer = createMockPlayer(ownerId);
        coordinator = new PetRuntimeCoordinator(null, mockRepository, mockDefRegistry, activeRegistry, mockEntityController, mockBehaviorController);

        PetInstance instanceB = new PetInstance(petB, ownerId, "cat", "PetB", 1, 0, PetAvailabilityState.AVAILABLE, 0, 0);
        PetDefinition defB = mockDefRegistry.find("wolf").get();

        assertThrows(RuntimeException.class, () -> coordinator.spawnAndRegister(mockPlayer, instanceB, defB));

        assertEquals(1, newPetSpawnCalls);
        assertEquals(1, newPetBehaviorInitCalls);
        assertEquals(1, databaseSwitchCalls);
        assertEquals(0, databaseRestoreCalls);
        assertEquals(1, restorePetSpawnCalls);
        assertEquals(1, restorePetBehaviorInitCalls);

        assertTrue(activeRegistry.getByOwner(ownerId).isPresent());
        assertEquals(petA, activeRegistry.getByOwner(ownerId).get().getPetId());
    }

    @Test
    void testRestoreFailsGracefullyWhenPreviousDefinitionMissing() {
        UUID ownerId = UUID.randomUUID();
        UUID petA = UUID.randomUUID();
        UUID petB = UUID.randomUUID();

        ActivePet activeA = new ActivePet(petA, ownerId, UUID.randomUUID(), null, PetRuntimeState.ACTIVE);
        activeRegistry.register(activeA);

        PetRepository mockRepository = createMockRepository(petA, ownerId);
        PetDefinitionRegistry mockDefRegistry = createMockDefRegistry(false); // Definition missing for rollback
        PetEntityController mockEntityController = createMockEntityController(petB, false);
        PetBehaviorController mockBehaviorController = createMockBehaviorController(false);

        Player mockPlayer = createMockPlayer(ownerId);
        coordinator = new PetRuntimeCoordinator(null, mockRepository, mockDefRegistry, activeRegistry, mockEntityController, mockBehaviorController);

        PetInstance instanceB = new PetInstance(petB, ownerId, "cat", "PetB", 1, 0, PetAvailabilityState.AVAILABLE, 0, 0);
        PetDefinition defB = new PetDefinition("wolf", "Wolf", Collections.emptyList(), EntityType.WOLF, false, false, true, false, true, true, 100, true, Collections.emptyList());

        assertThrows(RuntimeException.class, () -> coordinator.spawnAndRegister(mockPlayer, instanceB, defB));

        assertEquals(1, newPetSpawnCalls);
        assertEquals(0, restorePetSpawnCalls); // Restore spawn skipped because definition is missing
        assertTrue(activeRegistry.getByOwner(ownerId).isEmpty(), "Registry must be empty if previous definition was missing");
    }

    private PetRepository createMockRepository(UUID petA, UUID ownerId) {
        return new PetRepository() {
            @Override public Optional<PetInstance> findById(UUID id) { return Optional.of(new PetInstance(petA, ownerId, "wolf", "PetA", 1, 0, PetAvailabilityState.AVAILABLE, 0, 0)); }
            @Override public java.util.List<PetInstance> findByOwner(UUID ownerId) { return Collections.emptyList(); }
            @Override public Optional<PetInstance> findActiveByOwner(UUID ownerId) { return Optional.of(new PetInstance(petA, ownerId, "wolf", "PetA", 1, 0, PetAvailabilityState.AVAILABLE, 0, 0)); }
            @Override public void insert(PetInstance pet) {}
            @Override public void update(PetInstance pet) {}
            @Override public void delete(UUID petId) {}
            @Override public void setActivePet(UUID ownerId, UUID petId) {}
            @Override public void clearActivePet(UUID ownerId) {}
            @Override public void switchActivePet(UUID ownerId, UUID previousPetId, UUID newPetId) { databaseSwitchCalls++; }
            @Override public void clearActivePetAndSetAvailable(UUID ownerId, UUID petId) {}
            @Override public void restoreActivePet(UUID ownerId, UUID previousPetId, UUID failedPetId) { databaseRestoreCalls++; }
        };
    }

    private PetDefinitionRegistry createMockDefRegistry(boolean includeDefinition) {
        return new PetDefinitionRegistry() {
            @Override public Optional<PetDefinition> find(String id) {
                if (!includeDefinition) {
                    return Optional.empty();
                }
                return Optional.of(new PetDefinition("wolf", "Wolf", Collections.emptyList(), EntityType.WOLF, false, false, true, false, true, true, 100, true, Collections.emptyList()));
            }
            @Override public java.util.Collection<PetDefinition> getAll() { return Collections.emptyList(); }
            @Override public void reload() {}
        };
    }

    private PetEntityController createMockEntityController(UUID failTargetPetId, boolean failAlways) {
        return new PetEntityController() {
            @Override public Entity spawn(PetInstance instance, PetDefinition def, Player owner) {
                if (failAlways || instance.petId().equals(failTargetPetId)) {
                    newPetSpawnCalls++;
                    throw new RuntimeException("Simulated Spawn Failure for " + instance.petId());
                }
                if (newPetSpawnCalls == 0) {
                    newPetSpawnCalls++;
                } else {
                    restorePetSpawnCalls++;
                }
                return createFakeLivingEntity(UUID.randomUUID());
            }
            @Override public void remove(Entity entity) {}
            @Override public void updateName(Entity entity, PetInstance instance, PetDefinition def) {}
            @Override public boolean isValid(Entity entity) { return true; }
        };
    }

    private PetBehaviorController createMockBehaviorController(boolean failOnInit) {
        return new PetBehaviorController() {
            @Override public void initialize(ActivePet activePet, LivingEntity entity, Player owner) {
                if (restorePetSpawnCalls > 0) {
                    restorePetBehaviorInitCalls++;
                } else {
                    newPetBehaviorInitCalls++;
                    if (failOnInit) {
                        throw new RuntimeException("Simulated Behavior Init Failure");
                    }
                }
            }
            @Override public void tick(ActivePet activePet, LivingEntity entity, Player owner) {}
            @Override public void remove(ActivePet activePet, LivingEntity entity) {}
        };
    }

    private LivingEntity createFakeLivingEntity(UUID entityId) {
        return (LivingEntity) Proxy.newProxyInstance(
                LivingEntity.class.getClassLoader(),
                new Class<?>[]{LivingEntity.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> entityId;
                    case "isValid" -> true;
                    case "isDead" -> false;
                    case "getType" -> EntityType.WOLF;
                    default -> null;
                }
        );
    }

    private Player createMockPlayer(UUID ownerId) {
        return (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(),
                new Class<?>[]{Player.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getUniqueId")) {
                        return ownerId;
                    }
                    if (method.getName().equals("isOnline")) {
                        return true;
                    }
                    if (method.getName().equals("getName")) {
                        return "MockPlayer";
                    }
                    return null;
                }
        );
    }
}

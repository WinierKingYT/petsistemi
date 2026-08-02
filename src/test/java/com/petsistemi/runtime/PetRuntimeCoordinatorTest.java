package com.petsistemi.runtime;

import com.petsistemi.definition.PetDefinitionRegistry;
import com.petsistemi.domain.PetDefinition;
import com.petsistemi.domain.PetInstance;
import com.petsistemi.domain.PetRuntimeState;
import com.petsistemi.domain.PetStorageState;
import com.petsistemi.persistence.PetRepository;
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
    private boolean rollbackDbAttempted = false;
    private boolean physicalPetRestored = false;

    @BeforeEach
    void setUp() {
        activeRegistry = new ActivePetRegistry();
        rollbackDbAttempted = false;
        physicalPetRestored = false;
    }

    @Test
    void testNullOwnerThrowsIllegalArgumentException() {
        PetRepository mockRepository = createMockRepository(UUID.randomUUID(), UUID.randomUUID());
        PetDefinitionRegistry mockDefRegistry = createMockDefRegistry();
        PetEntityController mockEntityController = createMockEntityController(UUID.randomUUID(), false);
        PetBehaviorController mockBehaviorController = createMockBehaviorController(false);

        coordinator = new PetRuntimeCoordinator(null, mockRepository, mockDefRegistry, activeRegistry, mockEntityController, mockBehaviorController);
        PetInstance instanceB = new PetInstance(UUID.randomUUID(), UUID.randomUUID(), "cat", "PetB", 1, 0, PetStorageState.AVAILABLE, 0, 0);
        PetDefinition defB = mockDefRegistry.find("wolf").get();

        assertThrows(IllegalArgumentException.class, () -> coordinator.spawnAndRegister(null, instanceB, defB));
    }

    @Test
    void testPhysicalEntityAndRegistryRestoredOnSpawnFailure() {
        UUID ownerId = UUID.randomUUID();
        UUID petA = UUID.randomUUID();
        UUID petB = UUID.randomUUID();

        ActivePet activeA = new ActivePet(petA, ownerId, UUID.randomUUID(), null, PetRuntimeState.ACTIVE);
        activeRegistry.register(activeA);

        PetRepository mockRepository = createMockRepository(petA, ownerId);
        PetDefinitionRegistry mockDefRegistry = createMockDefRegistry();
        PetEntityController mockEntityController = createMockEntityController(petB, false); // Fails on petB spawn
        PetBehaviorController mockBehaviorController = createMockBehaviorController(false);

        Player mockPlayer = createMockPlayer(ownerId);
        coordinator = new PetRuntimeCoordinator(null, mockRepository, mockDefRegistry, activeRegistry, mockEntityController, mockBehaviorController);

        PetInstance instanceB = new PetInstance(petB, ownerId, "cat", "PetB", 1, 0, PetStorageState.AVAILABLE, 0, 0);
        PetDefinition defB = mockDefRegistry.find("wolf").get();

        assertThrows(RuntimeException.class, () -> coordinator.spawnAndRegister(mockPlayer, instanceB, defB));

        assertTrue(physicalPetRestored, "Previous physical pet must be restored upon spawn failure");
        assertTrue(activeRegistry.getByOwner(ownerId).isPresent(), "Pet A must be re-registered in activeRegistry");
        assertEquals(petA, activeRegistry.getByOwner(ownerId).get().getPetId());
    }

    @Test
    void testPhysicalEntityAndRegistryRestoredOnBehaviorInitFailure() {
        UUID ownerId = UUID.randomUUID();
        UUID petA = UUID.randomUUID();
        UUID petB = UUID.randomUUID();

        ActivePet activeA = new ActivePet(petA, ownerId, UUID.randomUUID(), null, PetRuntimeState.ACTIVE);
        activeRegistry.register(activeA);

        PetRepository mockRepository = createMockRepository(petA, ownerId);
        PetDefinitionRegistry mockDefRegistry = createMockDefRegistry();
        PetEntityController mockEntityController = createMockEntityController(UUID.randomUUID(), false);
        PetBehaviorController mockBehaviorController = createMockBehaviorController(true); // Fails on behavior init

        Player mockPlayer = createMockPlayer(ownerId);
        coordinator = new PetRuntimeCoordinator(null, mockRepository, mockDefRegistry, activeRegistry, mockEntityController, mockBehaviorController);

        PetInstance instanceB = new PetInstance(petB, ownerId, "cat", "PetB", 1, 0, PetStorageState.AVAILABLE, 0, 0);
        PetDefinition defB = mockDefRegistry.find("wolf").get();

        assertThrows(RuntimeException.class, () -> coordinator.spawnAndRegister(mockPlayer, instanceB, defB));

        assertTrue(physicalPetRestored, "Previous physical pet must be restored upon behavior init failure");
        assertTrue(activeRegistry.getByOwner(ownerId).isPresent(), "Pet A must be re-registered in activeRegistry");
        assertEquals(petA, activeRegistry.getByOwner(ownerId).get().getPetId());
    }

    @Test
    void testPhysicalEntityAndDbRestoredOnDatabaseSwitchFailure() {
        UUID ownerId = UUID.randomUUID();
        UUID petA = UUID.randomUUID();
        UUID petB = UUID.randomUUID();

        ActivePet activeA = new ActivePet(petA, ownerId, UUID.randomUUID(), null, PetRuntimeState.ACTIVE);
        activeRegistry.register(activeA);

        PetRepository mockRepository = new PetRepository() {
            @Override public Optional<PetInstance> findById(UUID id) { return Optional.of(new PetInstance(petA, ownerId, "wolf", "PetA", 1, 0, PetStorageState.ACTIVE, 0, 0)); }
            @Override public java.util.List<PetInstance> findByOwner(UUID ownerId) { return Collections.emptyList(); }
            @Override public Optional<PetInstance> findActiveByOwner(UUID ownerId) { return Optional.of(new PetInstance(petA, ownerId, "wolf", "PetA", 1, 0, PetStorageState.ACTIVE, 0, 0)); }
            @Override public void insert(PetInstance pet) {}
            @Override public void update(PetInstance pet) {}
            @Override public void delete(UUID petId) {}
            @Override public void setActivePet(UUID ownerId, UUID petId) {}
            @Override public void clearActivePet(UUID ownerId) {}
            @Override public void switchActivePet(UUID ownerId, UUID previousPetId, UUID newPetId) {
                throw new RuntimeException("Simulated DB Switch Failure");
            }
            @Override public void clearActivePetAndSetAvailable(UUID ownerId, UUID petId) {}
            @Override public void restoreActivePet(UUID ownerId, UUID previousPetId, UUID failedPetId) { rollbackDbAttempted = true; }
        };

        PetDefinitionRegistry mockDefRegistry = createMockDefRegistry();
        PetEntityController mockEntityController = createMockEntityController(UUID.randomUUID(), false);
        PetBehaviorController mockBehaviorController = createMockBehaviorController(false);

        Player mockPlayer = createMockPlayer(ownerId);
        coordinator = new PetRuntimeCoordinator(null, mockRepository, mockDefRegistry, activeRegistry, mockEntityController, mockBehaviorController);

        PetInstance instanceB = new PetInstance(petB, ownerId, "cat", "PetB", 1, 0, PetStorageState.AVAILABLE, 0, 0);
        PetDefinition defB = mockDefRegistry.find("wolf").get();

        assertThrows(RuntimeException.class, () -> coordinator.spawnAndRegister(mockPlayer, instanceB, defB));

        assertTrue(physicalPetRestored, "Previous physical pet must be restored upon DB switch failure");
        assertTrue(activeRegistry.getByOwner(ownerId).isPresent(), "Pet A must be re-registered in activeRegistry");
        assertEquals(petA, activeRegistry.getByOwner(ownerId).get().getPetId());
    }

    private PetRepository createMockRepository(UUID petA, UUID ownerId) {
        return new PetRepository() {
            @Override public Optional<PetInstance> findById(UUID id) { return Optional.of(new PetInstance(petA, ownerId, "wolf", "PetA", 1, 0, PetStorageState.ACTIVE, 0, 0)); }
            @Override public java.util.List<PetInstance> findByOwner(UUID ownerId) { return Collections.emptyList(); }
            @Override public Optional<PetInstance> findActiveByOwner(UUID ownerId) { return Optional.of(new PetInstance(petA, ownerId, "wolf", "PetA", 1, 0, PetStorageState.ACTIVE, 0, 0)); }
            @Override public void insert(PetInstance pet) {}
            @Override public void update(PetInstance pet) {}
            @Override public void delete(UUID petId) {}
            @Override public void setActivePet(UUID ownerId, UUID petId) {}
            @Override public void clearActivePet(UUID ownerId) {}
            @Override public void switchActivePet(UUID ownerId, UUID previousPetId, UUID newPetId) {}
            @Override public void clearActivePetAndSetAvailable(UUID ownerId, UUID petId) {}
            @Override public void restoreActivePet(UUID ownerId, UUID previousPetId, UUID failedPetId) { rollbackDbAttempted = true; }
        };
    }

    private PetDefinitionRegistry createMockDefRegistry() {
        return new PetDefinitionRegistry() {
            @Override public Optional<PetDefinition> find(String id) {
                return Optional.of(new PetDefinition("wolf", "Wolf", Collections.emptyList(), EntityType.WOLF, false, false, true, false, true, true, 100, true, Collections.emptyList()));
            }
            @Override public java.util.Collection<PetDefinition> getAll() { return Collections.emptyList(); }
            @Override public void reload() {}
        };
    }

    private PetEntityController createMockEntityController(UUID failTargetPetId, boolean failAlways) {
        return new PetEntityController() {
            @Override public org.bukkit.entity.Entity spawn(PetInstance instance, PetDefinition def, Player owner) {
                if (failAlways || instance.petId().equals(failTargetPetId)) {
                    throw new RuntimeException("Simulated Spawn Failure");
                }
                physicalPetRestored = true;
                return null;
            }
            @Override public void remove(org.bukkit.entity.Entity entity) {}
            @Override public void updateName(org.bukkit.entity.Entity entity, PetInstance instance, PetDefinition def) {}
            @Override public boolean isValid(org.bukkit.entity.Entity entity) { return false; }
        };
    }

    private PetBehaviorController createMockBehaviorController(boolean failOnInit) {
        return new PetBehaviorController() {
            @Override public void initialize(ActivePet activePet, LivingEntity entity, Player owner) {
                if (failOnInit) {
                    throw new RuntimeException("Simulated Behavior Init Failure");
                }
            }
            @Override public void tick(ActivePet activePet, LivingEntity entity, Player owner) {}
            @Override public void remove(ActivePet activePet, LivingEntity entity) {}
        };
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

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
        PetRepository mockRepository = new PetRepository() {
            @Override public Optional<PetInstance> findById(UUID id) { return Optional.empty(); }
            @Override public java.util.List<PetInstance> findByOwner(UUID ownerId) { return Collections.emptyList(); }
            @Override public Optional<PetInstance> findActiveByOwner(UUID ownerId) { return Optional.empty(); }
            @Override public void insert(PetInstance pet) {}
            @Override public void update(PetInstance pet) {}
            @Override public void delete(UUID petId) {}
            @Override public void setActivePet(UUID ownerId, UUID petId) {}
            @Override public void clearActivePet(UUID ownerId) {}
            @Override public void switchActivePet(UUID ownerId, UUID previousPetId, UUID newPetId) {}
            @Override public void clearActivePetAndSetAvailable(UUID ownerId, UUID petId) {}
            @Override public void restoreActivePet(UUID ownerId, UUID previousPetId, UUID failedPetId) {}
        };

        PetDefinitionRegistry mockDefRegistry = new PetDefinitionRegistry() {
            @Override public Optional<PetDefinition> find(String id) {
                return Optional.of(new PetDefinition("wolf", "Wolf", Collections.emptyList(), EntityType.WOLF, false, false, true, false, true, true, 100, true, Collections.emptyList()));
            }
            @Override public java.util.Collection<PetDefinition> getAll() { return Collections.emptyList(); }
            @Override public void reload() {}
        };

        PetEntityController mockEntityController = new PetEntityController() {
            @Override public org.bukkit.entity.Entity spawn(PetInstance instance, PetDefinition def, Player owner) { return null; }
            @Override public void remove(org.bukkit.entity.Entity entity) {}
            @Override public void updateName(org.bukkit.entity.Entity entity, PetInstance instance, PetDefinition def) {}
            @Override public boolean isValid(org.bukkit.entity.Entity entity) { return false; }
        };

        PetBehaviorController mockBehaviorController = new PetBehaviorController() {
            @Override public void initialize(ActivePet activePet, LivingEntity entity, Player owner) {}
            @Override public void tick(ActivePet activePet, LivingEntity entity, Player owner) {}
            @Override public void remove(ActivePet activePet, LivingEntity entity) {}
        };

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

        PetRepository mockRepository = new PetRepository() {
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

        PetDefinitionRegistry mockDefRegistry = new PetDefinitionRegistry() {
            @Override public Optional<PetDefinition> find(String id) {
                return Optional.of(new PetDefinition("wolf", "Wolf", Collections.emptyList(), EntityType.WOLF, false, false, true, false, true, true, 100, true, Collections.emptyList()));
            }
            @Override public java.util.Collection<PetDefinition> getAll() { return Collections.emptyList(); }
            @Override public void reload() {}
        };

        PetEntityController mockEntityController = new PetEntityController() {
            @Override public org.bukkit.entity.Entity spawn(PetInstance instance, PetDefinition def, Player owner) {
                if (instance.petId().equals(petB)) {
                    throw new RuntimeException("Simulated Spawn Failure for Pet B");
                }
                physicalPetRestored = true;
                return null;
            }
            @Override public void remove(org.bukkit.entity.Entity entity) {}
            @Override public void updateName(org.bukkit.entity.Entity entity, PetInstance instance, PetDefinition def) {}
            @Override public boolean isValid(org.bukkit.entity.Entity entity) { return false; }
        };

        PetBehaviorController mockBehaviorController = new PetBehaviorController() {
            @Override public void initialize(ActivePet activePet, LivingEntity entity, Player owner) {}
            @Override public void tick(ActivePet activePet, LivingEntity entity, Player owner) {}
            @Override public void remove(ActivePet activePet, LivingEntity entity) {}
        };

        Player mockPlayer = createMockPlayer(ownerId);

        coordinator = new PetRuntimeCoordinator(null, mockRepository, mockDefRegistry, activeRegistry, mockEntityController, mockBehaviorController);

        PetInstance instanceB = new PetInstance(petB, ownerId, "cat", "PetB", 1, 0, PetStorageState.AVAILABLE, 0, 0);
        PetDefinition defB = mockDefRegistry.find("wolf").get();

        assertThrows(RuntimeException.class, () -> coordinator.spawnAndRegister(mockPlayer, instanceB, defB));

        // Verify that restorePreviousRuntimePet was called for online player, re-registering Pet A
        assertTrue(physicalPetRestored, "Previous physical pet must be restored upon spawn failure");
        assertTrue(activeRegistry.getByOwner(ownerId).isPresent(), "Pet A must be re-registered in activeRegistry");
        assertEquals(petA, activeRegistry.getByOwner(ownerId).get().getPetId());
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

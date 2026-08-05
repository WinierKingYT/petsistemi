package com.petsistemi.runtime;

import com.petsistemi.definition.PetDefinitionRegistry;
import com.petsistemi.domain.PetDefinition;
import com.petsistemi.domain.PetInstance;
import com.petsistemi.domain.PetRuntimeState;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public class PetRuntimeCoordinator {

    public enum PetRemovalCause {
        PLAYER_QUIT,
        PLAYER_DISMISS,
        CHUNK_UNLOAD,
        ENTITY_DEATH,
        EXTERNAL_REMOVAL,
        PLUGIN_DISABLE,
        WORLD_CHANGE
    }

    private final JavaPlugin plugin;
    private final PetDefinitionRegistry definitionRegistry;
    private final ActivePetRegistry activeRegistry;
    private final PetEntityController entityController;
    private final PetBehaviorController behaviorController;
    private volatile PetRecoveryHandler recoveryHandler;

    public PetRuntimeCoordinator(JavaPlugin plugin,
                                 PetDefinitionRegistry definitionRegistry,
                                 ActivePetRegistry activeRegistry,
                                 PetEntityController entityController,
                                 PetBehaviorController behaviorController) {
        this.plugin = plugin;
        this.definitionRegistry = definitionRegistry;
        this.activeRegistry = activeRegistry;
        this.entityController = entityController;
        this.behaviorController = behaviorController;
    }

    /** Wires the recovery callback (set after construction to avoid circular dependencies). */
    public void setRecoveryHandler(PetRecoveryHandler recoveryHandler) {
        this.recoveryHandler = recoveryHandler;
    }

    public synchronized Entity spawnRuntimeUncommitted(Player owner, PetInstance pet, PetDefinition definition) throws Exception {
        Objects.requireNonNull(owner, "owner null olamaz.");
        Objects.requireNonNull(pet, "pet null olamaz.");
        Objects.requireNonNull(definition, "definition null olamaz.");

        UUID ownerId = owner.getUniqueId();
        despawnRuntime(ownerId);

        Entity spawnedEntity = Objects.requireNonNull(
                entityController.spawn(pet, definition, owner),
                "PetEntityController.spawn null entity döndü"
        );

        ActivePet uncommitted = new ActivePet(pet.petId(), ownerId, pet.definitionId(), pet.level(), spawnedEntity.getUniqueId(), spawnedEntity, PetRuntimeState.ACTIVE);
        if (spawnedEntity instanceof LivingEntity living) {
            behaviorController.initialize(uncommitted, living, owner);
        }

        return spawnedEntity;
    }

    public synchronized void commitRuntimeSpawn(ActivePet activePet) {
        Objects.requireNonNull(activePet, "activePet null olamaz.");
        activeRegistry.register(activePet);
    }

    public synchronized void rollbackRuntimeSpawn(UUID ownerId, Entity entity) {
        if (entity != null) {
            entityController.remove(entity);
        }
        if (ownerId != null) {
            activeRegistry.unregister(ownerId);
        }
    }

    public synchronized void despawnRuntime(UUID ownerId) {
        if (ownerId == null) return;
        Optional<ActivePet> activeOpt = activeRegistry.getByOwner(ownerId);
        if (activeOpt.isPresent()) {
            ActivePet active = activeOpt.get();
            Entity entity = active.getSpawnedEntity();
            if (entity instanceof LivingEntity living) {
                behaviorController.remove(active, living);
            }
            if (entity != null) {
                entityController.remove(entity);
            }
            activeRegistry.unregister(ownerId);
        }
    }

    public Optional<ActivePet> getRuntimePet(UUID ownerId) {
        if (ownerId == null) return Optional.empty();
        return activeRegistry.getByOwner(ownerId);
    }

    public synchronized void forceCleanupAll() {
        List<ActivePet> activeList = new ArrayList<>(activeRegistry.getAllActive());
        for (ActivePet active : activeList) {
            try {
                Entity entity = active.getSpawnedEntity();
                if (entity instanceof LivingEntity living) {
                    behaviorController.remove(active, living);
                }
                if (entity != null) {
                    entityController.remove(entity);
                }
            } catch (Exception e) {
                if (plugin != null) plugin.getLogger().warning("Shutdown sırasında entity temizleme uyarısı: " + e.getMessage());
            } finally {
                activeRegistry.unregister(active.getOwnerId());
            }
        }
        activeRegistry.clear();
    }

    public synchronized void runWatchdogCheck() {
        List<ActivePet> activeList = new ArrayList<>(activeRegistry.getAllActive());
        for (ActivePet active : activeList) {
            UUID ownerId = active.getOwnerId();
            Player owner = Bukkit.getPlayer(ownerId);
            Entity entity = active.getSpawnedEntity();

            if (owner == null || !owner.isOnline()) {
                despawnRuntime(ownerId);
                continue;
            }

            if (entity == null || !entity.isValid() || entity.isDead()) {
                despawnRuntime(ownerId);
                // Stage 7: attempt recovery instead of leaving the pet unspawned
                PetRecoveryHandler handler = recoveryHandler;
                if (handler != null) {
                    try {
                        handler.attemptRecovery(active, owner);
                    } catch (Exception e) {
                        if (plugin != null)
                            plugin.getLogger().warning("Watchdog kurtarma hatası (ownerId=" + ownerId + "): " + e.getMessage());
                    }
                }
            }
        }
    }
}

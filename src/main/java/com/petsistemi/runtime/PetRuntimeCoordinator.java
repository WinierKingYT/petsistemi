package com.petsistemi.runtime;

import com.petsistemi.definition.PetDefinitionRegistry;
import com.petsistemi.domain.PetDefinition;
import com.petsistemi.domain.PetInstance;
import com.petsistemi.domain.PetRuntimeState;
import com.petsistemi.domain.PetStorageState;
import com.petsistemi.persistence.PetRepository;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;

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
    private final PetRepository repository;
    private final PetDefinitionRegistry definitionRegistry;
    private final ActivePetRegistry activeRegistry;
    private final PetEntityController entityController;
    private final PetBehaviorController behaviorController;

    public PetRuntimeCoordinator(JavaPlugin plugin, PetRepository repository,
                                 PetDefinitionRegistry definitionRegistry,
                                 ActivePetRegistry activeRegistry,
                                 PetEntityController entityController,
                                 PetBehaviorController behaviorController) {
        this.plugin = plugin;
        this.repository = repository;
        this.definitionRegistry = definitionRegistry;
        this.activeRegistry = activeRegistry;
        this.entityController = entityController;
        this.behaviorController = behaviorController;
    }

    /**
     * Atomically spawns pet entity, initializes behavior, updates DB via single transaction, and registers runtime state.
     * Performs complete rollback if any step fails.
     */
    public synchronized Entity spawnAndRegister(Player owner, PetInstance pet, PetDefinition definition) throws Exception {
        UUID ownerId = owner.getUniqueId();
        
        // Find current active pet ID if any to switch state in DB transaction
        Optional<PetInstance> currentActiveDb = repository.findActiveByOwner(ownerId);
        UUID previousPetId = currentActiveDb.map(PetInstance::petId).orElse(null);

        // Despawn existing runtime entity if present
        despawnActiveEntity(ownerId);

        Entity spawnedEntity = null;
        try {
            // 1. Spawn entity via controller
            spawnedEntity = entityController.spawn(pet, definition, owner);

            // 2. Initialize behavior controller
            ActivePet activePet = new ActivePet(pet.petId(), ownerId, spawnedEntity.getUniqueId(), spawnedEntity, PetRuntimeState.ACTIVE);
            if (spawnedEntity instanceof LivingEntity living) {
                behaviorController.initialize(activePet, living, owner);
            }

            // 3. Single Transaction DB Switch (Resets old pet to AVAILABLE, updates active selection, sets new pet to ACTIVE)
            repository.switchActivePet(ownerId, previousPetId, pet.petId());

            // 4. Register in active runtime registry
            activeRegistry.register(activePet);

            return spawnedEntity;

        } catch (Exception e) {
            // ROLLBACK if database or initialization failed
            plugin.getLogger().log(Level.SEVERE, "Pet spawn işlemi sırasında hata oluştu, rollback yapılıyor: " + pet.petId(), e);
            if (spawnedEntity != null && spawnedEntity.isValid()) {
                entityController.remove(spawnedEntity);
            }
            activeRegistry.unregister(ownerId);
            try {
                repository.clearActivePetAndSetAvailable(ownerId, pet.petId());
            } catch (Exception ignored) {}

            throw e;
        }
    }

    /**
     * Centralized removal handler for all lifecycle events.
     */
    public synchronized void handleRemoval(UUID ownerId, PetRemovalCause cause) {
        Optional<ActivePet> activeOpt = activeRegistry.getByOwner(ownerId);
        UUID petId = activeOpt.map(ActivePet::getPetId).orElse(null);

        if (petId == null) {
            // Try fetching from DB selection if runtime entity was absent
            petId = repository.findActiveByOwner(ownerId).map(PetInstance::petId).orElse(null);
        }

        // Despawn physical entity and behavior
        despawnActiveEntity(ownerId);

        // Determine if selection in DB should be preserved or cleared
        switch (cause) {
            case PLAYER_QUIT:
            case CHUNK_UNLOAD:
            case WORLD_CHANGE:
                // Preserve selection in player_active_pets table across sessions/unloads
                break;

            case PLAYER_DISMISS:
            case ENTITY_DEATH:
            case EXTERNAL_REMOVAL:
            case PLUGIN_DISABLE:
                // Clear selection and set pet state to AVAILABLE
                try {
                    repository.clearActivePetAndSetAvailable(ownerId, petId);
                } catch (Exception e) {
                    plugin.getLogger().warning("DB temizleme hatası (" + cause + "): " + e.getMessage());
                }
                break;
        }
    }

    /**
     * Despawns entity on player quit without clearing the selected pet ID in DB.
     */
    public synchronized void despawnOnQuit(UUID ownerId) {
        handleRemoval(ownerId, PetRemovalCause.PLAYER_QUIT);
    }

    /**
     * Complete dismiss: removes entity AND clears active pet selection from DB.
     */
    public synchronized void dismissAndClear(UUID ownerId) {
        handleRemoval(ownerId, PetRemovalCause.PLAYER_DISMISS);
    }

    /**
     * Helper to despawn the physical entity and cleanup behavior/registry.
     */
    public synchronized void despawnActiveEntity(UUID ownerId) {
        Optional<ActivePet> activeOpt = activeRegistry.getByOwner(ownerId);
        if (activeOpt.isPresent()) {
            ActivePet active = activeOpt.get();
            Entity entity = active.getSpawnedEntity();
            if (entity instanceof LivingEntity living) {
                behaviorController.remove(active, living);
            }
            entityController.remove(entity);
            activeRegistry.unregister(ownerId);
        }
    }

    /**
     * Non-cancellable, guaranteed system cleanup during plugin shutdown.
     */
    public synchronized void forceCleanupAll() {
        List<ActivePet> activeList = new ArrayList<>(activeRegistry.getAllActive());
        for (ActivePet active : activeList) {
            try {
                Entity entity = active.getSpawnedEntity();
                if (entity instanceof LivingEntity living) {
                    behaviorController.remove(active, living);
                }
                entityController.remove(entity);
            } catch (Exception e) {
                plugin.getLogger().warning("Shutdown sırasında entity temizleme uyarısı: " + e.getMessage());
            } finally {
                activeRegistry.unregister(active.getOwnerId());
            }
        }
        activeRegistry.clear();
    }

    /**
     * Periodic Watchdog Task: repairs orphaned or destroyed entities.
     */
    public synchronized void runWatchdogCheck() {
        List<ActivePet> activeList = new ArrayList<>(activeRegistry.getAllActive());
        for (ActivePet active : activeList) {
            UUID ownerId = active.getOwnerId();
            Player owner = Bukkit.getPlayer(ownerId);
            Entity entity = active.getSpawnedEntity();

            // Case 1: Owner logged off
            if (owner == null || !owner.isOnline()) {
                handleRemoval(ownerId, PetRemovalCause.PLAYER_QUIT);
                continue;
            }

            // Case 2: Entity destroyed externally or invalid
            if (entity == null || !entity.isValid() || entity.isDead()) {
                plugin.getLogger().warning("Watchdog: Pet entitysi kaybolmuş tespit edildi (" + active.getPetId() + "). Runtime temizleniyor...");
                handleRemoval(ownerId, PetRemovalCause.EXTERNAL_REMOVAL);
            }
        }
    }
}

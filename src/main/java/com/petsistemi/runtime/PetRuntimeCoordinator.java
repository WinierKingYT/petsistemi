package com.petsistemi.runtime;

import com.petsistemi.api.PetSnapshot;
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
     * Atomically spawns pet entity, initializes behavior, updates DB, and registers runtime state.
     * Performs complete rollback if any step fails.
     */
    public synchronized Entity spawnAndRegister(Player owner, PetInstance pet, PetDefinition definition) throws Exception {
        // Despawn existing runtime entity if present
        despawnActiveEntity(owner.getUniqueId());

        Entity spawnedEntity = null;
        try {
            // 1. Spawn entity via controller
            spawnedEntity = entityController.spawn(pet, definition, owner);

            // 2. Initialize behavior controller
            if (spawnedEntity instanceof LivingEntity living) {
                behaviorController.initialize(new ActivePet(pet.petId(), owner.getUniqueId(), spawnedEntity.getUniqueId(), spawnedEntity, PetRuntimeState.ACTIVE), living, owner);
            }

            // 3. Database updates (atomic transaction logic)
            repository.setActivePet(owner.getUniqueId(), pet.petId());
            repository.update(pet.withStorageState(PetStorageState.ACTIVE));

            // 4. Register in active runtime registry
            ActivePet activePet = new ActivePet(pet.petId(), owner.getUniqueId(), spawnedEntity.getUniqueId(), spawnedEntity, PetRuntimeState.ACTIVE);
            activeRegistry.register(activePet);

            return spawnedEntity;

        } catch (Exception e) {
            // ROLLBACK if database or initialization failed
            plugin.getLogger().log(Level.SEVERE, "Pet spawn işlemi sırasında hata oluştu, rollback yapılıyor: " + pet.petId(), e);
            if (spawnedEntity != null && spawnedEntity.isValid()) {
                entityController.remove(spawnedEntity);
            }
            activeRegistry.unregister(owner.getUniqueId());
            try {
                repository.update(pet.withStorageState(PetStorageState.AVAILABLE));
                repository.clearActivePet(owner.getUniqueId());
            } catch (Exception ignored) {}

            throw e;
        }
    }

    /**
     * Despawns entity on player quit without clearing the selected pet ID in DB,
     * so that the selected pet is restored when the player logs back in.
     */
    public synchronized void despawnOnQuit(Player player) {
        despawnActiveEntity(player.getUniqueId());
    }

    /**
     * Complete dismiss: removes entity AND clears active pet selection from DB.
     */
    public synchronized void dismissAndClear(Player owner) {
        Optional<ActivePet> activeOpt = activeRegistry.getByOwner(owner.getUniqueId());
        if (activeOpt.isPresent()) {
            ActivePet active = activeOpt.get();
            despawnActiveEntity(owner.getUniqueId());
            repository.clearActivePet(owner.getUniqueId());
            repository.findById(active.getPetId()).ifPresent(pet ->
                repository.update(pet.withStorageState(PetStorageState.AVAILABLE))
            );
        } else {
            // Clear DB record just in case
            repository.clearActivePet(owner.getUniqueId());
        }
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
            }
        }
        activeRegistry.getAllActive().clear();
    }

    /**
     * Periodic Watchdog Task: repairs orphaned or destroyed entities.
     */
    public synchronized void runWatchdogCheck() {
        List<ActivePet> activeList = new ArrayList<>(activeRegistry.getAllActive());
        for (ActivePet active : activeList) {
            Player owner = Bukkit.getPlayer(active.getOwnerId());
            Entity entity = active.getSpawnedEntity();

            // Case 1: Owner logged off
            if (owner == null || !owner.isOnline()) {
                despawnOnQuit(Bukkit.getOfflinePlayer(active.getOwnerId()).getPlayer() != null ? owner : owner);
                continue;
            }

            // Case 2: Entity destroyed externally or invalid
            if (entity == null || !entity.isValid() || entity.isDead()) {
                plugin.getLogger().warning("Watchdog: Pet entitysi kaybolmuş tespit edildi (" + active.getPetId() + "). Runtime temizleniyor...");
                despawnActiveEntity(active.getOwnerId());
                repository.findById(active.getPetId()).ifPresent(p ->
                    repository.update(p.withStorageState(PetStorageState.AVAILABLE))
                );
            }
        }
    }
}

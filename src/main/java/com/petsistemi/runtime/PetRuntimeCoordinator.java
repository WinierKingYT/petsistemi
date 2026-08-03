package com.petsistemi.runtime;

import com.petsistemi.definition.PetDefinitionRegistry;
import com.petsistemi.domain.PetDefinition;
import com.petsistemi.domain.PetInstance;
import com.petsistemi.domain.PetRuntimeState;
import com.petsistemi.persistence.PetRepository;
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

    public synchronized void restoreOnJoin(Player owner) {
        if (owner == null || !owner.isOnline()) return;

        // Double-register guard: if a valid pet is already spawned, skip
        Optional<ActivePet> existing = activeRegistry.getByOwner(owner.getUniqueId());
        if (existing.isPresent()) {
            Entity e = existing.get().getSpawnedEntity();
            if (e != null && e.isValid() && !e.isDead()) return;
            // Stale registry entry — clean up before restoring
            activeRegistry.unregister(owner.getUniqueId());
        }

        Optional<PetInstance> selectedOpt = repository.findActiveByOwner(owner.getUniqueId());
        if (selectedOpt.isPresent()) {
            PetInstance pet = selectedOpt.get();
            if (pet.availabilityState() == com.petsistemi.domain.PetAvailabilityState.DISABLED) {
                if (plugin != null) plugin.getLogger().warning(
                        "Çevrimiçi oyuncunun seçili peti DISABLED olduğu için restore edilmedi ve seçim temizlendi: " + pet.petId());
                repository.clearActivePetAndSetAvailable(owner.getUniqueId(), pet.petId());
                return;
            }
            definitionRegistry.find(pet.definitionId()).ifPresent(def -> {
                try {
                    spawnAndRegister(owner, pet, def);
                } catch (Exception e) {
                    if (plugin != null) {
                        plugin.getLogger().log(Level.SEVERE, "Join restore hatası: " + e.getMessage(), e);
                    }
                }
            });
        }
    }

    public synchronized Entity spawnAndRegister(Player owner, PetInstance pet, PetDefinition definition) throws Exception {
        if (owner == null) {
            throw new IllegalArgumentException("Oyuncu (owner) null olamaz.");
        }
        if (pet == null || pet.availabilityState() == com.petsistemi.domain.PetAvailabilityState.DISABLED) {
            throw new IllegalArgumentException("Devre dışı bırakılmış petler (DISABLED) çağrılamaz.");
        }
        UUID ownerId = owner.getUniqueId();
        
        Optional<PetInstance> currentActiveDb = repository.findActiveByOwner(ownerId);
        UUID previousPetId = currentActiveDb.map(PetInstance::petId).orElse(null);

        Optional<ActivePet> previousRuntime = activeRegistry.getByOwner(ownerId);
        boolean hadPreviousRuntime = previousRuntime.isPresent();

        despawnActiveEntity(ownerId);

        Entity spawnedEntity = null;
        boolean databaseSwitched = false;
        try {
            // 1. Spawn entity via controller (Must be non-null)
            spawnedEntity = Objects.requireNonNull(
                    entityController.spawn(pet, definition, owner),
                    "PetEntityController.spawn null entity döndü"
            );

            // 2. Initialize behavior controller
            ActivePet activePet = new ActivePet(pet.petId(), ownerId, spawnedEntity.getUniqueId(), spawnedEntity, PetRuntimeState.ACTIVE);
            if (spawnedEntity instanceof LivingEntity living) {
                behaviorController.initialize(activePet, living, owner);
            }

            // 3. Single Transaction DB Switch
            repository.switchActivePet(ownerId, previousPetId, pet.petId());
            databaseSwitched = true;

            // 4. Register in active runtime registry
            activeRegistry.register(activePet);

            return spawnedEntity;

        } catch (Exception e) {
            if (plugin != null) {
                plugin.getLogger().log(Level.SEVERE, "Pet spawn işlemi sırasında hata oluştu, rollback yapılıyor: " + pet.petId(), e);
            }
            if (spawnedEntity != null && spawnedEntity.isValid()) {
                entityController.remove(spawnedEntity);
            }
            activeRegistry.unregister(ownerId);

            if (databaseSwitched) {
                try {
                    repository.restoreActivePet(ownerId, previousPetId, pet.petId());
                } catch (Exception ex) {
                    if (plugin != null) {
                        plugin.getLogger().log(Level.SEVERE, "Rollback DB restore hatası!", ex);
                    }
                }
            }

            if (hadPreviousRuntime && previousPetId != null && owner.isOnline()) {
                restorePreviousRuntimePet(owner, previousPetId);
            }

            throw e;
        }
    }

    private void restorePreviousRuntimePet(Player owner, UUID previousPetId) {
        Optional<PetInstance> previousPetOpt = repository.findById(previousPetId);
        if (previousPetOpt.isEmpty()) {
            if (plugin != null) plugin.getLogger().severe("Rollback sırasında eski pet kaydı veritabanında bulunamadı! Pet ID: " + previousPetId);
            return;
        }

        PetInstance previousPet = previousPetOpt.get();
        Optional<PetDefinition> previousDefOpt = definitionRegistry.find(previousPet.definitionId());
        if (previousDefOpt.isEmpty()) {
            if (plugin != null) plugin.getLogger().severe("Rollback sırasında eski pet tür tanımı bulunamadı! Tür ID: " + previousPet.definitionId());
            return;
        }

        PetDefinition previousDef = previousDefOpt.get();
        try {
            Entity restoredEntity = Objects.requireNonNull(
                    entityController.spawn(previousPet, previousDef, owner),
                    "Eski pet geri yüklenirken entityController.spawn null döndü"
            );

            ActivePet restoredActive = new ActivePet(previousPet.petId(), owner.getUniqueId(), restoredEntity.getUniqueId(), restoredEntity, PetRuntimeState.ACTIVE);
            if (restoredEntity instanceof LivingEntity living) {
                behaviorController.initialize(restoredActive, living, owner);
            }
            activeRegistry.register(restoredActive);
            if (plugin != null) plugin.getLogger().info("Eski pet (" + previousPet.definitionId() + ") fiziki varlığı başarıyla geri yüklendi.");
        } catch (Exception e) {
            if (plugin != null) plugin.getLogger().log(Level.SEVERE, "Eski pet fiziki varlığı geri yüklenirken kritik hata: " + e.getMessage(), e);
        }
    }

    /**
     * Centralized removal handler for all lifecycle events.
     */
    public synchronized void handleRemoval(UUID ownerId, PetRemovalCause cause) {
        Optional<ActivePet> activeOpt = activeRegistry.getByOwner(ownerId);
        UUID petId = activeOpt.map(ActivePet::getPetId).orElse(null);

        if (petId == null) {
            petId = repository.findActiveByOwner(ownerId).map(PetInstance::petId).orElse(null);
        }

        despawnActiveEntity(ownerId);

        switch (cause) {
            case PLAYER_QUIT:
            case WORLD_CHANGE:
                break;

            case CHUNK_UNLOAD:
                Player onlineOwner = Bukkit.getPlayer(ownerId);
                if (onlineOwner != null && onlineOwner.isOnline() && petId != null) {
                    final UUID finalPetId = petId;
                    schedulePendingRestoreWithRetry(onlineOwner, finalPetId, 1);
                }
                break;

            case PLAYER_DISMISS:
            case ENTITY_DEATH:
            case EXTERNAL_REMOVAL:
            case PLUGIN_DISABLE:
                try {
                    repository.clearActivePetAndSetAvailable(ownerId, petId);
                } catch (Exception e) {
                    if (plugin != null) plugin.getLogger().warning("DB temizleme hatası (" + cause + "): " + e.getMessage());
                }
                break;
        }
    }

    private void schedulePendingRestoreWithRetry(Player owner, UUID petId, int attempt) {
        if (attempt > 3 || owner == null || !owner.isOnline()) return;

        long delayTicks = attempt == 1 ? 20L : (attempt == 2 ? 60L : 200L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (owner.isOnline() && activeRegistry.getByOwner(owner.getUniqueId()).isEmpty()) {
                Optional<PetInstance> selected = repository.findActiveByOwner(owner.getUniqueId());
                if (selected.isPresent() && selected.get().petId().equals(petId)) {
                    PetInstance p = selected.get();
                    Optional<PetDefinition> defOpt = definitionRegistry.find(p.definitionId());
                    if (defOpt.isEmpty()) {
                        if (plugin != null) plugin.getLogger().warning("Pet tanımı (" + p.definitionId() + ") bulunamadı, restore denemesi (" + attempt + "/3) erteleniyor.");
                        schedulePendingRestoreWithRetry(owner, petId, attempt + 1);
                        return;
                    }

                    PetDefinition def = defOpt.get();
                    try {
                        spawnAndRegister(owner, p, def);
                    } catch (Exception e) {
                        if (plugin != null) plugin.getLogger().warning("Pet restore denemesi (" + attempt + "/3) başarısız: " + e.getMessage());
                        schedulePendingRestoreWithRetry(owner, petId, attempt + 1);
                    }
                }
            }
        }, delayTicks);
    }

    public synchronized void despawnOnQuit(UUID ownerId) {
        handleRemoval(ownerId, PetRemovalCause.PLAYER_QUIT);
    }

    public synchronized void dismissAndClear(UUID ownerId) {
        handleRemoval(ownerId, PetRemovalCause.PLAYER_DISMISS);
    }

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

            // Owner went offline — clean up silently
            if (owner == null || !owner.isOnline()) {
                handleRemoval(ownerId, PetRemovalCause.PLAYER_QUIT);
                continue;
            }

            // Entity disappeared (chunk unload, external plugin removal, server bug)
            if (entity == null || !entity.isValid() || entity.isDead()) {
                if (plugin != null) plugin.getLogger().warning(
                        "Watchdog: Pet entitysi kaybolmuş tespit edildi (" + active.getPetId()
                        + "). Otomatik restore deneniyor...");
                // Unregister the stale entry first
                activeRegistry.unregister(ownerId);
                // Attempt restore on the next tick (owner is online)
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (owner.isOnline()) restoreOnJoin(owner);
                });
            }
        }
    }
}

package com.petsistemi.runtime;

import com.petsistemi.definition.PetDefinitionRegistry;
import com.petsistemi.domain.PetDefinition;
import com.petsistemi.domain.PetInstance;
import com.petsistemi.domain.PetMovementDefinition;
import com.petsistemi.domain.PetMovementType;
import com.petsistemi.domain.PetRuntimeState;
import com.petsistemi.domain.RuntimeRepresentationType;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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
    private final PetDefinitionRegistry definitionRegistry;
    private final ActivePetRegistry activeRegistry;
    private final PetEntityController entityController;
    private final PetBehaviorController behaviorController;
    private final PetRepresentationRegistry representationRegistry;
    private final PetMovementRegistry movementRegistry;

    /**
     * Uncommitted handles awaiting DB commit; needed so a rollback can also clean up
     * child entities that were spawned but never registered.
     */
    private final Map<UUID, ActivePet> pendingSpawns = new HashMap<>();

    /** Pets whose tick failure has already been logged; cleared once they tick cleanly again. */
    private final Set<UUID> tickFailureLogged = new HashSet<>();

    private volatile PetRecoveryHandler recoveryHandler;
    private volatile PetIdleSleepController idleSleepController;
    private volatile PetTransformController transformController;
    private volatile PetEmoteController emoteController;
    private volatile InteractionHitboxController hitboxController;
    private volatile PetBuffController buffController;

    public PetRuntimeCoordinator(JavaPlugin plugin,
                                 PetDefinitionRegistry definitionRegistry,
                                 ActivePetRegistry activeRegistry,
                                 PetEntityController entityController,
                                 PetBehaviorController behaviorController) {
        this(plugin, definitionRegistry, activeRegistry, entityController, behaviorController, null, null);
    }

    public PetRuntimeCoordinator(JavaPlugin plugin,
                                 PetDefinitionRegistry definitionRegistry,
                                 ActivePetRegistry activeRegistry,
                                 PetEntityController entityController,
                                 PetBehaviorController behaviorController,
                                 PetRepresentationRegistry representationRegistry,
                                 PetMovementRegistry movementRegistry) {
        this.plugin = plugin;
        this.definitionRegistry = definitionRegistry;
        this.activeRegistry = activeRegistry;
        this.entityController = entityController;
        this.behaviorController = behaviorController;
        this.representationRegistry = representationRegistry;
        this.movementRegistry = movementRegistry;
    }

    /** Wires the recovery callback (set after construction to avoid circular dependencies). */
    public void setRecoveryHandler(PetRecoveryHandler recoveryHandler) {
        this.recoveryHandler = recoveryHandler;
    }

    /** Wires the idle/sleep controller (set after construction). */
    public void setIdleSleepController(PetIdleSleepController idleSleepController) {
        this.idleSleepController = idleSleepController;
    }

    /** Wires the transform controller (set after construction). */
    public void setTransformController(PetTransformController transformController) {
        this.transformController = transformController;
    }

    /** Wires the emote controller (set after construction). */
    public void setEmoteController(PetEmoteController emoteController) {
        this.emoteController = emoteController;
    }

    public void setHitboxController(InteractionHitboxController hitboxController) {
        this.hitboxController = hitboxController;
    }

    public void setBuffController(PetBuffController buffController) {
        this.buffController = buffController;
    }

    /**
     * Spawns via the modular representation/movement pipeline when a representation
     * controller is registered for the definition's type; otherwise falls back to the
     * legacy {@link PetEntityController} path.
     */
    public synchronized ActivePet spawnRuntimeUncommittedHandle(Player owner, PetInstance pet, PetDefinition definition) throws Exception {
        Objects.requireNonNull(owner, "owner null olamaz.");
        Objects.requireNonNull(pet, "pet null olamaz.");
        Objects.requireNonNull(definition, "definition null olamaz.");

        UUID ownerId = owner.getUniqueId();
        despawnRuntime(ownerId);

        RuntimeRepresentationType repType = definition.representationOrEntity().type();
        PetRepresentationController repController = resolveRepresentation(repType);
        if (repController != null) {
            Entity spawnedEntity = Objects.requireNonNull(
                    repController.spawn(pet, definition, owner),
                    "Representation controller spawn null entity döndü");

            ActivePet uncommitted = new ActivePet(pet.petId(), ownerId, pet.definitionId(), pet.level(),
                    spawnedEntity.getUniqueId(), spawnedEntity, PetRuntimeState.ACTIVE);
            uncommitted.setPetInstance(pet);

            PetMovementDefinition movementDef = definition.movement();
            PetMovementType movementType = movementDef != null ? movementDef.type() : PetMovementType.GROUND_FOLLOW;
            uncommitted.setRepresentationType(repType);
            uncommitted.setMovementType(movementType);
            uncommitted.setMovementDefinition(movementDef);
            uncommitted.setUpdateIntervalTicks(movementDef != null ? movementDef.updateIntervalTicks() : 5);

            List<Entity> children = repController.spawnChildren(spawnedEntity, pet, definition, owner);
            for (Entity child : children) {
                uncommitted.addChild(child);
            }

            PetMovementController movement = resolveMovement(uncommitted);
            if (movement != null) {
                movement.initialize(uncommitted, spawnedEntity, owner);
            }
            pendingSpawns.put(ownerId, uncommitted);
            return uncommitted;
        }

        Entity spawnedEntity = Objects.requireNonNull(
                entityController.spawn(pet, definition, owner),
                "PetEntityController.spawn null entity döndü"
        );

        ActivePet uncommitted = new ActivePet(pet.petId(), ownerId, pet.definitionId(), pet.level(),
                spawnedEntity.getUniqueId(), spawnedEntity, PetRuntimeState.ACTIVE);
        uncommitted.setPetInstance(pet);
        uncommitted.setUpdateIntervalTicks(5);
        if (spawnedEntity instanceof LivingEntity living && behaviorController != null) {
            behaviorController.initialize(uncommitted, living, owner);
        }
        return uncommitted;
    }

    public synchronized Entity spawnRuntimeUncommitted(Player owner, PetInstance pet, PetDefinition definition) throws Exception {
        return spawnRuntimeUncommittedHandle(owner, pet, definition).getSpawnedEntity();
    }

    public synchronized void commitRuntimeSpawn(ActivePet activePet) {
        Objects.requireNonNull(activePet, "activePet null olamaz.");
        if (activePet.getOwnerId() != null) {
            pendingSpawns.remove(activePet.getOwnerId());
        }
        activeRegistry.register(activePet);
    }

    public synchronized void rollbackRuntimeSpawn(UUID ownerId, Entity entity) {
        if (ownerId != null) {
            ActivePet pending = pendingSpawns.remove(ownerId);
            if (pending != null) {
                removeChildren(pending);
            }
        }
        if (entity != null) {
            removeEntityFromRegistry(null, entity);
        }
        if (ownerId != null) {
            activeRegistry.unregister(ownerId);
        }
    }

    public synchronized void despawnRuntime(UUID ownerId) {
        if (ownerId == null) return;
        PetIdleSleepController idle = idleSleepController;
        if (idle != null) {
            idle.cleanup(ownerId);
        }
        PetTransformController transforms = transformController;
        if (transforms != null) {
            transforms.cleanup(ownerId);
        }
        PetEmoteController emotes = emoteController;
        if (emotes != null) {
            emotes.cleanup(ownerId);
        }
        Optional<ActivePet> activeOpt = activeRegistry.getByOwner(ownerId);
        if (activeOpt.isPresent()) {
            ActivePet active = activeOpt.get();
            if (hitboxController != null) {
                hitboxController.removeHitbox(active.getPetId());
            }
            cleanupRuntime(active, active.getSpawnedEntity());
            activeRegistry.unregister(ownerId);
        }
    }

    /**
     * Single tick entrypoint for the runtime tick task: dispatches each active pet
     * to its registered movement controller (respecting per-pet update intervals),
     * falling back to the legacy behavior controller.
     */
    public synchronized void tickAll() {
        if (buffController != null) {
            buffController.tick(activeRegistry, definitionRegistry);
        }
        tickEach(new ArrayList<>(activeRegistry.getAllActive()), Bukkit::getPlayer);
    }

    /**
     * Ticks every pet in isolation. A controller that throws must not stop the pets
     * queued behind it — otherwise one broken pet silently freezes everyone else's.
     * Failures are logged once per pet and re-armed when that pet ticks cleanly again.
     *
     * <p>{@code ownerLookup} is a seam so the loop can be driven without a live server.</p>
     */
    synchronized void tickEach(List<ActivePet> pets, OwnerLookup ownerLookup) {
        for (ActivePet active : pets) {
            Player owner = ownerLookup.find(active.getOwnerId());
            if (owner == null || !owner.isOnline()) continue;

            // Smart Chunk Unload Suspend: skip tick if owner location/chunk is unloaded to save CPU
            if (owner.getWorld() != null && owner.getLocation() != null && owner.getLocation().getChunk() != null && !owner.getLocation().getChunk().isLoaded()) {
                continue;
            }

            try {
                tickPet(active, owner);
                tickFailureLogged.remove(active.getPetId());
            } catch (Exception e) {
                reportTickFailure(active, e);
            }
        }
    }

    /** Resolves the owner of a pet; {@link Bukkit#getPlayer(UUID)} in production. */
    @FunctionalInterface
    interface OwnerLookup {
        Player find(UUID ownerId);
    }

    private void tickPet(ActivePet active, Player owner) {
        Entity entity = active.getSpawnedEntity();
        if (entity == null || !entity.isValid()) return;

        // Max Distance Teleport Guard: if pet is > 50 blocks away, auto teleport next to owner
        if (entity.getWorld() != null && owner.getWorld() != null && entity.getWorld().equals(owner.getWorld()) && entity.getLocation() != null && owner.getLocation() != null) {
            try {
                if (entity.getLocation().distanceSquared(owner.getLocation()) > 2500.0) {
                    entity.teleport(SafePetLocationFinder.findSafeLocation(owner.getLocation()));
                    return;
                }
            } catch (Exception ignored) {}
        }

        if (hitboxController != null) {
            hitboxController.updateHitbox(active, definitionRegistry);
        }

        int interval = active.getUpdateIntervalTicks();
        if (interval > 0) {
            active.incrementTickAccumulator();
            if (active.getTickAccumulator() < interval) return;
            active.setTickAccumulator(0);
        }

        PetMovementController movement = resolveMovement(active);
        if (movement != null) {
            movement.tick(active, entity, owner);
        } else if (entity instanceof LivingEntity living && behaviorController != null) {
            behaviorController.tick(active, living, owner);
        }

        PetTransformController transforms = transformController;
        if (transforms != null) {
            transforms.tick(owner, active, entity);
        }

        PetIdleSleepController idle = idleSleepController;
        if (idle != null) {
            idle.tick(owner, active, entity);
        }

        tickVisual(active, entity, owner);
    }

    private void reportTickFailure(ActivePet active, Exception e) {
        if (plugin == null || plugin.getLogger() == null) return;
        if (tickFailureLogged.add(active.getPetId())) {
            plugin.getLogger().log(Level.WARNING,
                    "Pet tick hatası — bu pet atlandı, diğerleri etkilenmedi (ownerId=" + active.getOwnerId()
                            + ", petId=" + active.getPetId() + ", movement=" + active.getMovementType() + ")", e);
        }
    }

    private void tickVisual(ActivePet active, Entity entity, Player owner) {
        PetRepresentationController rep = resolveRepresentation(active.getRepresentationType());
        if (rep == null) return;
        PetDefinition definition = resolveVisualDefinition(active);
        if (definition != null) {
            rep.tickVisual(entity, active.getPetInstance(), definition, owner);
        }
    }

    private PetDefinition resolveVisualDefinition(ActivePet active) {
        PetDefinition base = definitionRegistry != null
                ? definitionRegistry.find(active.getDefinitionId()).orElse(null)
                : null;
        PetTransformController transforms = transformController;
        if (transforms == null || base == null) {
            return base;
        }
        PetDefinition derived = transforms.activeDefinition(active);
        return derived != null ? derived : base;
    }

    /**
     * Refreshes the visuals of a currently active pet from a fresh persisted state
     * (level-up, rename). Updates the stored instance and re-renders the representation.
     */
    public synchronized void refreshVisual(UUID ownerId, PetInstance freshInstance) {
        if (ownerId == null || freshInstance == null) return;
        Optional<ActivePet> activeOpt = activeRegistry.getByOwner(ownerId);
        if (activeOpt.isEmpty() || !activeOpt.get().getPetId().equals(freshInstance.petId())) return;

        ActivePet active = activeOpt.get();
        active.setPetInstance(freshInstance);

        PetRepresentationController rep = resolveRepresentation(active.getRepresentationType());
        if (rep == null) return;
        PetDefinition definition = resolveVisualDefinition(active);
        if (definition != null && active.getSpawnedEntity() != null) {
            rep.updateVisual(active.getSpawnedEntity(), freshInstance, definition);
            if (active.isResting()) {
                rep.applyRestState(active.getSpawnedEntity(), freshInstance, definition, true);
            }
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
                cleanupRuntime(active, active.getSpawnedEntity());
            } catch (Exception e) {
                if (plugin != null) plugin.getLogger().warning("Shutdown sırasında entity temizleme uyarısı: " + e.getMessage());
            } finally {
                activeRegistry.unregister(active.getOwnerId());
            }
        }
        activeRegistry.clear();

        // Clean up any uncommitted (never committed) spawn handles
        for (ActivePet pending : new ArrayList<>(pendingSpawns.values())) {
            try {
                cleanupRuntime(pending, pending.getSpawnedEntity());
            } catch (Exception e) {
                if (plugin != null) plugin.getLogger().warning("Shutdown sırasında pending entity temizleme uyarısı: " + e.getMessage());
            }
        }
        pendingSpawns.clear();
    }

    public synchronized void runWatchdogCheck() {
        List<ActivePet> activeList = new ArrayList<>(activeRegistry.getAllActive());
        for (ActivePet active : activeList) {
            UUID ownerId = active.getOwnerId();
            // One pet's failure must not abort the sweep for the rest.
            try {
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
                        handler.attemptRecovery(active, owner);
                    }
                }
            } catch (Exception e) {
                if (plugin != null && plugin.getLogger() != null) {
                    plugin.getLogger().log(Level.WARNING,
                            "Watchdog hatası (ownerId=" + ownerId + ") — bu pet atlandı", e);
                }
            }
        }
    }

    private void cleanupRuntime(ActivePet active, Entity entity) {
        if (active != null) {
            tickFailureLogged.remove(active.getPetId());
        }
        PetMovementController movement = resolveMovement(active);
        if (movement != null) {
            movement.remove(active, entity);
        } else if (entity instanceof LivingEntity living && behaviorController != null) {
            behaviorController.remove(active, living);
        }
        if (entity != null) {
            removeEntityFromRegistry(active, entity);
        }
        if (active != null) {
            removeChildren(active);
        }
    }

    private void removeChildren(ActivePet active) {
        for (Entity child : active.getChildren()) {
            if (child != null && child.isValid()) {
                try {
                    child.remove();
                } catch (Exception ignored) {}
            }
        }
        active.clearChildren();
    }

    private void removeEntityFromRegistry(ActivePet active, Entity entity) {
        PetRepresentationController rep = resolveRepresentation(active != null ? active.getRepresentationType() : null);
        if (rep != null) {
            rep.remove(entity);
        } else if (entityController != null) {
            entityController.remove(entity);
        }
    }

    private PetRepresentationController resolveRepresentation(RuntimeRepresentationType type) {
        if (representationRegistry == null || type == null) return null;
        return representationRegistry.get(type);
    }

    private PetMovementController resolveMovement(ActivePet active) {
        if (movementRegistry == null || active == null) return null;
        PetMovementType type = active.getMovementType();
        return type != null ? movementRegistry.get(type) : null;
    }
}

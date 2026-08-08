package com.petsistemi.application;

import com.petsistemi.api.PetSnapshot;
import com.petsistemi.api.event.PetDismissEvent;
import com.petsistemi.api.event.PetEvolutionEvent;
import com.petsistemi.api.event.PetPreDismissEvent;
import com.petsistemi.api.event.PetPreEvolutionEvent;
import com.petsistemi.api.event.PetPreSummonEvent;
import com.petsistemi.api.event.PetRecoveryFailedEvent;
import com.petsistemi.api.event.PetSummonEvent;
import com.petsistemi.api.result.PetDismissResult;
import com.petsistemi.api.result.PetEvolutionResult;
import com.petsistemi.api.result.PetSummonResult;
import com.petsistemi.bootstrap.MainThreadDispatcher;
import com.petsistemi.definition.PetDefinitionRegistry;
import com.petsistemi.domain.PetAvailabilityState;
import com.petsistemi.domain.PetDefinition;
import com.petsistemi.domain.PetFollowMode;
import com.petsistemi.domain.PetInstance;
import com.petsistemi.domain.PetRuntimeState;
import com.petsistemi.domain.PetSelection;
import com.petsistemi.persistence.DatabaseExecutor;
import com.petsistemi.persistence.PetRepository;
import com.petsistemi.persistence.PetSelectionRepository;
import com.petsistemi.persistence.PlayerPetProfileCache;
import com.petsistemi.runtime.ActivePet;
import com.petsistemi.runtime.PetRuntimeCoordinator;
import com.petsistemi.runtime.RecoveryOutcome;
import com.petsistemi.runtime.recovery.PetRecoveryQueue;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Orchestrates summon/dismiss/restore runtime operations with strict thread-boundary enforcement.
 *
 * <p>Thread invariants:
 * <ul>
 *   <li>All DB/JDBC calls run exclusively on the DatabaseExecutor thread.</li>
 *   <li>All Bukkit API calls (spawn, event, entity, player message) run on the Bukkit main thread.</li>
 *   <li>Selection state is owned solely by {@link PetSelectionRepository}; PetRepository legacy methods
 *       are never called from this class.</li>
 * </ul>
 */
public class PetRuntimeOperationService {

    private final JavaPlugin plugin;
    private final PetRepository repository;
    private final PetSelectionRepository selectionRepository;
    private final PetDefinitionRegistry definitionRegistry;
    private final PetRuntimeCoordinator coordinator;
    private final PlayerPetProfileCache profileCache;
    private final DatabaseExecutor dbExecutor;
    private final MainThreadDispatcher mainThreadDispatcher;

    // Per-owner operation lock. Summon AND dismiss share this lock so they serialize per owner.
    private final Map<UUID, Object> ownerLocks = new ConcurrentHashMap<>();

    // Deduplicates watchdog recovery attempts per pet.
    private final PetRecoveryQueue recoveryQueue = new PetRecoveryQueue();

    public PetRuntimeOperationService(JavaPlugin plugin,
                                      PetRepository repository,
                                      PetSelectionRepository selectionRepository,
                                      PetDefinitionRegistry definitionRegistry,
                                      PetRuntimeCoordinator coordinator,
                                      PlayerPetProfileCache profileCache,
                                      DatabaseExecutor dbExecutor,
                                      MainThreadDispatcher mainThreadDispatcher) {
        this.plugin = plugin;
        this.repository = repository;
        this.selectionRepository = selectionRepository;
        this.definitionRegistry = definitionRegistry;
        this.coordinator = coordinator;
        this.profileCache = profileCache;
        this.dbExecutor = dbExecutor;
        this.mainThreadDispatcher = mainThreadDispatcher;
    }

    public CompletableFuture<PetSummonResult> summonAsync(Player owner, UUID petId) {
        Objects.requireNonNull(owner, "owner null olamaz.");
        Objects.requireNonNull(petId, "petId null olamaz.");
        UUID ownerId = owner.getUniqueId();

        Object lockMarker = new Object();
        if (ownerLocks.putIfAbsent(ownerId, lockMarker) != null) {
            return CompletableFuture.completedFuture(
                    new PetSummonResult(false, "Zaten devam eden bir pet çağırma/gönderme işleminiz var."));
        }

        CompletableFuture<PetSummonResult> future = new CompletableFuture<>();

        // ── Phase 1: DB – validate pet + read current selection ──────────────
        dbExecutor.submit(() -> {
            Optional<PetInstance> petOpt = repository.findById(petId);
            if (petOpt.isEmpty())
                return new InternalSummonState(false, "Pet veritabanında bulunamadı.", null, null, null, PetFollowMode.FOLLOW);

            PetInstance pet = petOpt.get();
            if (!pet.ownerId().equals(ownerId))
                return new InternalSummonState(false, "Bu pet size ait değil.", null, null, null, PetFollowMode.FOLLOW);
            if (pet.availabilityState() == PetAvailabilityState.DISABLED)
                return new InternalSummonState(false, "Devre dışı bırakılmış petler (DISABLED) çağrılamaz.", null, null, null, PetFollowMode.FOLLOW);

            PetDefinition def = definitionRegistry.find(pet.definitionId()).orElse(null);
            if (def == null)
                return new InternalSummonState(false,
                        "Pet türü konfigürasyonda bulunamadı: " + pet.definitionId(), null, null, null, PetFollowMode.FOLLOW);

            Optional<PetSelection> selOpt = selectionRepository.findByOwner(ownerId);
            UUID previousPetId = selOpt.map(PetSelection::petId).orElse(null);
            PetFollowMode followMode = selOpt.map(PetSelection::followMode).orElse(PetFollowMode.FOLLOW);

            return new InternalSummonState(true, null, pet, def, previousPetId, followMode);

        }).thenAccept(state -> {
            if (!state.success()) {
                ownerLocks.remove(ownerId, lockMarker);
                future.complete(new PetSummonResult(false, state.message()));
                return;
            }

            // ── Phase 2: Main thread – pre-event + uncommitted entity spawn ──
            mainThreadDispatcher.run(() -> {
                if (!owner.isOnline()) {
                    ownerLocks.remove(ownerId, lockMarker);
                    future.complete(new PetSummonResult(false, "Oyuncu çevrimdışı olduğu için çağırma iptal edildi."));
                    return;
                }

                // A definition may gate itself behind a permission node; pets without one stay open to everyone.
                String requiredPermission = state.definition().permission();
                if (requiredPermission != null && !owner.hasPermission(requiredPermission)) {
                    ownerLocks.remove(ownerId, lockMarker);
                    future.complete(new PetSummonResult(false,
                            "Bu pet türünü kullanma yetkiniz yok (" + requiredPermission + ")."));
                    return;
                }

                // Build pre-summon snapshot with correct selected/spawned flags
                boolean currentlySelected = Objects.equals(state.previousPetId(), petId);
                boolean currentlySpawned = coordinator.getRuntimePet(ownerId)
                        .map(a -> a.getPetId().equals(petId)).orElse(false);
                PetSnapshot preSnapshot = new PetSnapshot(
                        state.pet().petId(), state.pet().ownerId(), state.pet().definitionId(),
                        state.pet().customName(), state.pet().level(), state.pet().experience(),
                        state.pet().availabilityState(), currentlySelected, currentlySpawned);

                PetPreSummonEvent preEvent = new PetPreSummonEvent(owner, preSnapshot);
                if (Bukkit.getServer() != null) Bukkit.getPluginManager().callEvent(preEvent);
                if (preEvent.isCancelled()) {
                    ownerLocks.remove(ownerId, lockMarker);
                    future.complete(new PetSummonResult(false, "Pet çağrı işlemi başka bir eklenti tarafından engellendi."));
                    return;
                }

                // Capture previous runtime handle BEFORE spawning new entity
                Optional<ActivePet> previousRuntime = coordinator.getRuntimePet(ownerId);

                ActivePet uncommittedHandle;
                try {
                    uncommittedHandle = coordinator.spawnRuntimeUncommittedHandle(owner, state.pet(), state.definition());
                } catch (Exception e) {
                    ownerLocks.remove(ownerId, lockMarker);
                    future.complete(new PetSummonResult(false, "Pet varlığı oluşturulamadı: " + e.getMessage()));
                    return;
                }
                uncommittedHandle.setFollowMode(state.followMode());
                Entity uncommittedEntity = uncommittedHandle.getSpawnedEntity();

                ActivePet newActivePet = uncommittedHandle;

                // ── Phase 3: DB – atomic selection switch (selectionRepository ONLY) ──
                dbExecutor.submit(() -> {
                    try {
                        // Stage 2 requirement: ONLY selectionRepository touches selection table
                        selectionRepository.switchSelection(ownerId, state.previousPetId(), state.pet().petId());
                        return true;
                    } catch (Exception ex) {
                        if (plugin != null)
                            plugin.getLogger().log(Level.SEVERE, "DB selection switch hatası (summon)!", ex);
                        return false;
                    }
                }).thenCompose(dbSuccess -> mainThreadDispatcher.run(() -> {
                    try {
                        if (!dbSuccess) {
                            // DB failed → rollback the uncommitted entity; restore previous runtime if possible
                            coordinator.rollbackRuntimeSpawn(ownerId, uncommittedEntity);
                            // If there was a previous runtime pet, re-register it
                            previousRuntime.ifPresent(prev -> {
                                if (prev.getSpawnedEntity() != null && prev.getSpawnedEntity().isValid()) {
                                    try { coordinator.commitRuntimeSpawn(prev); } catch (Exception ignored) {}
                                }
                            });
                            future.complete(new PetSummonResult(false, "Veritabanı seçimi güncellenemedi, çağırma geri alındı."));
                            return;
                        }

                        // DB succeeded → commit new runtime
                        coordinator.commitRuntimeSpawn(newActivePet);

                        // Update cache
                        if (profileCache != null) {
                            profileCache.updateRuntimeState(ownerId, state.pet().petId(), state.pet().petId());
                        }

                        // Build final snapshot (selected=true, spawned=true)
                        PetSnapshot finalSnapshot = new PetSnapshot(
                                state.pet().petId(), state.pet().ownerId(), state.pet().definitionId(),
                                state.pet().customName(), state.pet().level(), state.pet().experience(),
                                state.pet().availabilityState(), true, true);

                        PetSummonEvent summonEvent = new PetSummonEvent(owner, finalSnapshot, uncommittedEntity);
                        if (Bukkit.getServer() != null) Bukkit.getPluginManager().callEvent(summonEvent);

                        future.complete(new PetSummonResult(true, "Pet başarıyla çağırıldı."));

                    } catch (Exception ex) {
                        // Main-thread commit failure → compensation DB rollback
                        if (plugin != null)
                            plugin.getLogger().log(Level.SEVERE, "Summon commit sonrası main-thread hatası! DB geri alınıyor.", ex);

                        coordinator.rollbackRuntimeSpawn(ownerId, uncommittedEntity);

                        // Compensation: restore previous selection in DB
                        dbExecutor.submit(() -> {
                            try {
                                selectionRepository.switchSelection(ownerId, state.pet().petId(), state.previousPetId());
                            } catch (Exception compEx) {
                                if (plugin != null)
                                    plugin.getLogger().log(Level.SEVERE, "Compensation DB rollback da başarısız!", compEx);
                            }
                            return null;
                        });

                        // Restore cache
                        if (profileCache != null) {
                            profileCache.updateRuntimeState(ownerId, state.previousPetId(), null);
                        }

                        future.complete(new PetSummonResult(false, "Sistem hatası: " + ex.getMessage()));
                    } finally {
                        ownerLocks.remove(ownerId, lockMarker);
                    }
                })).exceptionally(ex -> {
                    coordinator.rollbackRuntimeSpawn(ownerId, uncommittedEntity);
                    ownerLocks.remove(ownerId, lockMarker);
                    future.complete(new PetSummonResult(false, "Beklenmeyen hata: " + ex.getMessage()));
                    return null;
                });
            });

        }).exceptionally(ex -> {
            ownerLocks.remove(ownerId, lockMarker);
            future.complete(new PetSummonResult(false, "Hata oluştu: " + ex.getMessage()));
            return null;
        });

        return future;
    }

    /**
     * Persists the follow mode for the owner's selected pet and applies it to the
     * currently active runtime pet (if any). Runs the DB write on the database thread.
     */
    public CompletableFuture<Boolean> setFollowModeAsync(Player owner, PetFollowMode mode) {
        Objects.requireNonNull(owner, "owner null olamaz.");
        Objects.requireNonNull(mode, "mode null olamaz.");
        UUID ownerId = owner.getUniqueId();

        return dbExecutor.submit(() -> {
            try {
                selectionRepository.updateFollowMode(ownerId, mode);
                return true;
            } catch (Exception e) {
                if (plugin != null) {
                    plugin.getLogger().log(Level.WARNING, "Follow mode kaydedilemedi (ownerId=" + ownerId + "): " + e.getMessage());
                }
                return false;
            }
        }).thenCompose(persisted -> mainThreadDispatcher.run(() -> {
            if (persisted) {
                coordinator.getRuntimePet(ownerId).ifPresent(active -> active.setFollowMode(mode));
            }
        }).thenApply(v -> persisted));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PERSISTENT EVOLUTION
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Permanently changes a pet's definition while preserving its identity and progression.
     * If the pet is active, the runtime representation is replaced on the main thread. A
     * failed runtime replacement compensates the database write and restores the old pet.
     */
    public CompletableFuture<PetEvolutionResult> evolveAsync(Player owner, UUID petId, String targetDefinitionId) {
        Objects.requireNonNull(owner, "owner null olamaz.");
        Objects.requireNonNull(petId, "petId null olamaz.");
        String targetId = targetDefinitionId == null ? "" : targetDefinitionId.trim().toLowerCase(java.util.Locale.ROOT);
        if (targetId.isEmpty()) {
            return CompletableFuture.completedFuture(new PetEvolutionResult(false, "Hedef pet tanımı belirtilmedi.", null, null));
        }

        UUID ownerId = owner.getUniqueId();
        Object lockMarker = new Object();
        if (ownerLocks.putIfAbsent(ownerId, lockMarker) != null) {
            return CompletableFuture.completedFuture(new PetEvolutionResult(false, "Zaten devam eden bir pet işleminiz var.", null, null));
        }

        CompletableFuture<PetEvolutionResult> future = new CompletableFuture<>();
        dbExecutor.submit(() -> {
            PetInstance pet = repository.findById(petId).orElse(null);
            if (pet == null) return new InternalEvolutionState(false, "Pet veritabanında bulunamadı.", null, null, null, false);
            if (!ownerId.equals(pet.ownerId())) return new InternalEvolutionState(false, "Bu pet size ait değil.", null, null, null, false);
            if (pet.availabilityState() == PetAvailabilityState.DISABLED) return new InternalEvolutionState(false, "Devre dışı pet evrimleştirilemez.", null, null, null, false);
            if (pet.definitionId().equalsIgnoreCase(targetId)) return new InternalEvolutionState(false, "Pet zaten bu türe ait.", null, null, null, false);
            PetDefinition target = definitionRegistry.find(targetId).orElse(null);
            if (target == null) return new InternalEvolutionState(false, "Hedef pet tanımı bulunamadı: " + targetId, null, null, null, false);
            PetDefinition source = definitionRegistry.find(pet.definitionId()).orElse(null);
            if (source == null) return new InternalEvolutionState(false, "Mevcut pet tanımı bulunamadı: " + pet.definitionId(), null, null, null, false);
            boolean selected = selectionRepository.findByOwner(ownerId)
                    .map(selection -> selection.petId().equals(petId)).orElse(false);
            return new InternalEvolutionState(true, null, pet, pet.withDefinitionId(target.id()), target, selected);
        }).thenAccept(state -> {
            if (!state.success()) {
                ownerLocks.remove(ownerId, lockMarker);
                future.complete(new PetEvolutionResult(false, state.message(), null, null));
                return;
            }

            mainThreadDispatcher.run(() -> {
                if (!owner.isOnline()) {
                    ownerLocks.remove(ownerId, lockMarker);
                    future.complete(new PetEvolutionResult(false, "Oyuncu çevrimdışı olduğu için evrim iptal edildi.", null, null));
                    return;
                }
                String permission = state.targetDefinition().permission();
                if (permission != null && !owner.hasPermission(permission)) {
                    ownerLocks.remove(ownerId, lockMarker);
                    future.complete(new PetEvolutionResult(false, "Hedef pet türü için yetkiniz yok (" + permission + ").", null, null));
                    return;
                }

                Optional<ActivePet> active = coordinator.getRuntimePet(ownerId)
                        .filter(runtime -> runtime.getPetId().equals(petId));
                boolean selected = state.selected();
                PetSnapshot before = snapshot(state.before(), selected, active.isPresent());
                PetSnapshot after = snapshot(state.after(), selected, active.isPresent());
                PetPreEvolutionEvent preEvent = new PetPreEvolutionEvent(owner, before, state.after().definitionId());
                if (Bukkit.getServer() != null) Bukkit.getPluginManager().callEvent(preEvent);
                if (preEvent.isCancelled()) {
                    ownerLocks.remove(ownerId, lockMarker);
                    future.complete(new PetEvolutionResult(false, "Pet evrimi başka bir eklenti tarafından engellendi.", before, null));
                    return;
                }

                PetFollowMode followMode = active.map(ActivePet::getFollowMode).orElse(PetFollowMode.FOLLOW);
                org.bukkit.Location stayLocation = active.map(ActivePet::getStayLocation).orElse(null);
                dbExecutor.submit(() -> {
                    try {
                        repository.update(state.after());
                        return true;
                    } catch (Exception ex) {
                        if (plugin != null) plugin.getLogger().log(Level.SEVERE, "Pet evrimi kaydedilemedi.", ex);
                        return false;
                    }
                }).thenCompose(saved -> mainThreadDispatcher.run(() -> {
                    if (!saved) {
                        ownerLocks.remove(ownerId, lockMarker);
                        future.complete(new PetEvolutionResult(false, "Pet evrimi veritabanına kaydedilemedi.", before, null));
                        return;
                    }

                    Entity evolvedEntity = null;
                    try {
                        if (active.isPresent()) {
                            ActivePet replacement = coordinator.spawnRuntimeUncommittedHandle(owner, state.after(), state.targetDefinition());
                            replacement.setFollowMode(followMode);
                            replacement.setStayLocation(stayLocation);
                            coordinator.commitRuntimeSpawn(replacement);
                            evolvedEntity = replacement.getSpawnedEntity();
                        }
                        if (profileCache != null) profileCache.updateDefinition(ownerId, petId, state.after().definitionId());
                        if (Bukkit.getServer() != null) Bukkit.getPluginManager().callEvent(new PetEvolutionEvent(owner, before, after, evolvedEntity));
                        ownerLocks.remove(ownerId, lockMarker);
                        future.complete(new PetEvolutionResult(true, "Pet kalıcı olarak evrimleşti: " + state.after().definitionId(), before, after));
                    } catch (Exception runtimeError) {
                        coordinator.rollbackRuntimeSpawn(ownerId, null);
                        rollbackEvolution(owner, state, followMode, stayLocation, before, future, ownerId,
                                lockMarker, active.isPresent(), runtimeError);
                    }
                })).exceptionally(ex -> {
                    ownerLocks.remove(ownerId, lockMarker);
                    future.complete(new PetEvolutionResult(false, "Pet evrimi sırasında hata oluştu: " + ex.getMessage(), before, null));
                    return null;
                });
            }).exceptionally(ex -> {
                ownerLocks.remove(ownerId, lockMarker);
                future.complete(new PetEvolutionResult(false, "Pet evrimi sırasında hata oluştu: " + ex.getMessage(), null, null));
                return null;
            });
        }).exceptionally(ex -> {
            ownerLocks.remove(ownerId, lockMarker);
            future.complete(new PetEvolutionResult(false, "Pet evrimi sırasında hata oluştu: " + ex.getMessage(), null, null));
            return null;
        });
        return future;
    }

    private void rollbackEvolution(Player owner, InternalEvolutionState state, PetFollowMode followMode,
                                   org.bukkit.Location stayLocation, PetSnapshot before,
                                   CompletableFuture<PetEvolutionResult> future, UUID ownerId,
                                   Object lockMarker, boolean restoreRuntime, Exception runtimeError) {
        dbExecutor.submit(() -> {
            try {
                repository.update(state.before());
                return true;
            } catch (Exception rollbackError) {
                if (plugin != null) plugin.getLogger().log(Level.SEVERE, "Pet evrimi DB geri alımı başarısız.", rollbackError);
                return false;
            }
        }).thenCompose(rolledBack -> mainThreadDispatcher.run(() -> {
            boolean runtimeRestored = true;
            if (rolledBack && restoreRuntime && owner.isOnline()) {
                try {
                    PetDefinition source = definitionRegistry.find(state.before().definitionId()).orElseThrow();
                    ActivePet restored = coordinator.spawnRuntimeUncommittedHandle(owner, state.before(), source);
                    restored.setFollowMode(followMode);
                    restored.setStayLocation(stayLocation);
                    coordinator.commitRuntimeSpawn(restored);
                } catch (Exception restoreError) {
                    runtimeRestored = false;
                    coordinator.rollbackRuntimeSpawn(ownerId, null);
                    if (plugin != null) plugin.getLogger().log(Level.SEVERE, "Evrim sonrası eski runtime geri yüklenemedi.", restoreError);
                }
            }
            if (rolledBack && profileCache != null) {
                profileCache.updateDefinition(ownerId, state.before().petId(), state.before().definitionId());
            }
            String suffix = rolledBack && runtimeRestored ? " Değişiklik geri alındı." : " Geri alma tamamlanamadı; sunucu günlüğünü kontrol edin.";
            ownerLocks.remove(ownerId, lockMarker);
            future.complete(new PetEvolutionResult(false, "Yeni pet görünümü oluşturulamadı: " + runtimeError.getMessage() + suffix, before, null));
        })).exceptionally(ex -> {
            ownerLocks.remove(ownerId, lockMarker);
            future.complete(new PetEvolutionResult(false, "Evrim geri alınırken hata oluştu: " + ex.getMessage(), before, null));
            return null;
        });
    }

    private static PetSnapshot snapshot(PetInstance pet, boolean selected, boolean spawned) {
        return new PetSnapshot(pet.petId(), pet.ownerId(), pet.definitionId(), pet.customName(),
                pet.level(), pet.experience(), pet.availabilityState(), selected, spawned);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DISMISS
    // ─────────────────────────────────────────────────────────────────────────

    public CompletableFuture<PetDismissResult> dismissAsync(Player owner) {
        Objects.requireNonNull(owner, "owner null olamaz.");
        UUID ownerId = owner.getUniqueId();

        Object lockMarker = new Object();
        if (ownerLocks.putIfAbsent(ownerId, lockMarker) != null) {
            return CompletableFuture.completedFuture(
                    new PetDismissResult(false, "Zaten devam eden bir pet işleminiz var."));
        }

        CompletableFuture<PetDismissResult> future = new CompletableFuture<>();

        // ── Phase 1: Main thread – get runtime pet + pre-event ───────────────
        mainThreadDispatcher.run(() -> {
            Optional<ActivePet> activeOpt = coordinator.getRuntimePet(ownerId);
            if (activeOpt.isEmpty()) {
                ownerLocks.remove(ownerId, lockMarker);
                future.complete(new PetDismissResult(false, "Aktif çağırılmış bir petiniz bulunmuyor."));
                return;
            }

            ActivePet activePet = activeOpt.get();

            // Build dismiss snapshot from cache (not artificial zeros)
            PetSnapshot snapshot = buildDismissSnapshot(ownerId, activePet);

            PetPreDismissEvent preEvent = new PetPreDismissEvent(owner, snapshot);
            if (Bukkit.getServer() != null) Bukkit.getPluginManager().callEvent(preEvent);
            if (preEvent.isCancelled()) {
                ownerLocks.remove(ownerId, lockMarker);
                future.complete(new PetDismissResult(false, "Pet kaldırma işlemi başka bir eklenti tarafından engellendi."));
                return;
            }

            // ── Phase 2: DB – clear selection (selectionRepository ONLY) ────
            dbExecutor.submit(() -> {
                try {
                    selectionRepository.clear(ownerId);
                    return true;
                } catch (Exception e) {
                    if (plugin != null)
                        plugin.getLogger().log(Level.SEVERE, "DB dismiss clear hatası!", e);
                    return false;
                }
            }).thenCompose(dbSuccess -> mainThreadDispatcher.run(() -> {
                try {
                    if (!dbSuccess) {
                        future.complete(new PetDismissResult(false, "Veritabanı seçimi temizlenemedi, pet kaldırılmadı."));
                        return;
                    }

                    // DB succeeded → update cache + despawn
                    if (profileCache != null) {
                        profileCache.clearSelection(ownerId);
                        profileCache.clearSpawnedPet(ownerId);
                    }

                    coordinator.despawnRuntime(ownerId);

                    PetDismissEvent dismissEvent = new PetDismissEvent(owner, snapshot);
                    if (Bukkit.getServer() != null) Bukkit.getPluginManager().callEvent(dismissEvent);

                    future.complete(new PetDismissResult(true, "Pet başarıyla gönderildi."));

                } catch (Exception ex) {
                    if (plugin != null)
                        plugin.getLogger().log(Level.SEVERE, "Dismiss main-thread commit hatası!", ex);
                    future.completeExceptionally(ex);
                } finally {
                    ownerLocks.remove(ownerId, lockMarker);
                }
            })).exceptionally(ex -> {
                ownerLocks.remove(ownerId, lockMarker);
                future.completeExceptionally(ex);
                return null;
            });
        });

        return future;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // RESTORE
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Restores the player's DB-selected pet after a world change or reconnect.
     * The returned future completes only when the full summon pipeline finishes.
     */
    public CompletableFuture<Void> restoreSelectedPetAsync(Player owner) {
        if (owner == null || !owner.isOnline()) return CompletableFuture.completedFuture(null);
        UUID ownerId = owner.getUniqueId();

        return dbExecutor.submit(() -> selectionRepository.findByOwner(ownerId))
                .thenCompose(selOpt -> {
                    if (selOpt.isEmpty()) return CompletableFuture.completedFuture(null);
                    PetSelection sel = selOpt.get();

                    // Validate the pet is still available
                    return dbExecutor.submit(() -> repository.findById(sel.petId()))
                            .thenCompose(petOpt -> {
                                if (petOpt.isEmpty()) return CompletableFuture.completedFuture(null);
                                PetInstance pet = petOpt.get();
                                if (pet.availabilityState() == PetAvailabilityState.DISABLED)
                                    return CompletableFuture.completedFuture(null);
                                // Stage 7: compose summonAsync so the returned future truly completes with it
                                return summonAsync(owner, pet.petId()).thenApply(res -> (Void) null);
                            });
                }).exceptionally(ex -> {
                    if (plugin != null)
                        plugin.getLogger().log(Level.WARNING, "Pet restore hatası (ownerId=" + ownerId + "): " + ex.getMessage(), ex);
                    return null;
                });
    }

    /**
     * Watchdog recovery entry point: re-validates the DB selection and re-summons
     * the pet when its entity was lost (death/chunk unload) while the owner is online.
     * Deduplicated via {@link PetRecoveryQueue}; never blocks the caller thread.
     */
    public void recoverPetAsync(ActivePet activePet, Player owner) {
        if (activePet == null) return;
        UUID ownerId = activePet.getOwnerId();
        UUID petId = activePet.getPetId();

        if (owner == null || !owner.isOnline()) {
            fireRecoveryFailed(ownerId, petId, RecoveryOutcome.OWNER_OFFLINE);
            return;
        }

        if (!recoveryQueue.tryStart(ownerId, petId)) {
            return; // already pending — one recovery attempt per pet
        }

        dbExecutor.submit(() -> selectionRepository.findByOwner(ownerId))
                .thenCompose(selOpt -> {
                    if (selOpt.isEmpty() || !selOpt.get().petId().equals(petId)) {
                        recoveryQueue.clear(petId);
                        fireRecoveryFailed(ownerId, petId, RecoveryOutcome.SELECTION_CHANGED);
                        return CompletableFuture.completedFuture(null);
                    }
                    return dbExecutor.submit(() -> repository.findById(petId))
                            .thenCompose(petOpt -> {
                                if (petOpt.isEmpty()) {
                                    recoveryQueue.clear(petId);
                                    fireRecoveryFailed(ownerId, petId, RecoveryOutcome.DEFINITION_MISSING);
                                    return CompletableFuture.<PetSummonResult>completedFuture(null);
                                }
                                PetInstance pet = petOpt.get();
                                if (pet.availabilityState() == PetAvailabilityState.DISABLED) {
                                    recoveryQueue.clear(petId);
                                    fireRecoveryFailed(ownerId, petId, RecoveryOutcome.PET_DISABLED);
                                    return CompletableFuture.<PetSummonResult>completedFuture(null);
                                }
                                return summonAsync(owner, petId);
                            });
                })
                .thenAccept(result -> {
                    recoveryQueue.clear(petId);
                    if (result != null && !result.success()) {
                        fireRecoveryFailed(ownerId, petId, RecoveryOutcome.RETRIES_EXHAUSTED);
                    }
                })
                .exceptionally(ex -> {
                    recoveryQueue.clear(petId);
                    if (plugin != null)
                        plugin.getLogger().log(Level.WARNING,
                                "Pet kurtarma hatası (ownerId=" + ownerId + ", petId=" + petId + "): " + ex.getMessage(), ex);
                    fireRecoveryFailed(ownerId, petId, RecoveryOutcome.DATABASE_ERROR);
                    return null;
                });
    }

    private void fireRecoveryFailed(UUID ownerId, UUID petId, RecoveryOutcome outcome) {
        if (Bukkit.getServer() == null) return; // unit-test environment — no event bus
        if (mainThreadDispatcher == null) return;
        mainThreadDispatcher.run(() -> {
            if (Bukkit.getServer() != null) {
                Bukkit.getPluginManager().callEvent(new PetRecoveryFailedEvent(ownerId, petId, outcome));
            }
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Builds a dismiss snapshot using real data from the cache where available,
     * avoiding artificial null names and zero XP values.
     */
    private PetSnapshot buildDismissSnapshot(UUID ownerId, ActivePet activePet) {
        if (profileCache != null) {
            Optional<com.petsistemi.persistence.PlayerPetProfile> profileOpt = profileCache.getProfile(ownerId);
            if (profileOpt.isPresent()) {
                PetSnapshot cached = profileOpt.get().pets().get(activePet.getPetId());
                if (cached != null) {
                    return new PetSnapshot(
                            cached.petId(), cached.ownerId(), cached.definitionId(),
                            cached.customName(), cached.level(), cached.experience(),
                            cached.availabilityState(), true, true);
                }
            }
        }
        // Fallback: minimal snapshot (level from active pet, name unknown)
        return new PetSnapshot(
                activePet.getPetId(), ownerId, activePet.getDefinitionId(),
                null, activePet.getLevel(), 0, PetAvailabilityState.AVAILABLE, true, true);
    }

    private record InternalSummonState(boolean success, String message, PetInstance pet,
                                       PetDefinition definition, UUID previousPetId, PetFollowMode followMode) {}
    private record InternalEvolutionState(boolean success, String message, PetInstance before,
                                          PetInstance after, PetDefinition targetDefinition, boolean selected) {}
}

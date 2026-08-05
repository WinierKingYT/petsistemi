package com.petsistemi.application;

import com.petsistemi.api.PetSnapshot;
import com.petsistemi.api.event.PetDismissEvent;
import com.petsistemi.api.event.PetPreDismissEvent;
import com.petsistemi.api.event.PetPreSummonEvent;
import com.petsistemi.api.event.PetSummonEvent;
import com.petsistemi.api.result.PetDismissResult;
import com.petsistemi.api.result.PetSummonResult;
import com.petsistemi.bootstrap.MainThreadDispatcher;
import com.petsistemi.definition.PetDefinitionRegistry;
import com.petsistemi.domain.PetAvailabilityState;
import com.petsistemi.domain.PetDefinition;
import com.petsistemi.domain.PetInstance;
import com.petsistemi.domain.PetRuntimeState;
import com.petsistemi.persistence.DatabaseExecutor;
import com.petsistemi.persistence.PetRepository;
import com.petsistemi.persistence.PetSelectionRepository;
import com.petsistemi.persistence.PlayerPetProfileCache;
import com.petsistemi.runtime.ActivePet;
import com.petsistemi.runtime.PetRuntimeCoordinator;
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

public class PetRuntimeOperationService {

    private final JavaPlugin plugin;
    private final PetRepository repository;
    private final PetSelectionRepository selectionRepository;
    private final PetDefinitionRegistry definitionRegistry;
    private final PetRuntimeCoordinator coordinator;
    private final PlayerPetProfileCache profileCache;
    private final DatabaseExecutor dbExecutor;
    private final MainThreadDispatcher mainThreadDispatcher;

    private final Map<UUID, Object> ownerLocks = new ConcurrentHashMap<>();

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
            return CompletableFuture.completedFuture(new PetSummonResult(false, "Zaten devam eden bir pet çağırma işleminiz var."));
        }

        CompletableFuture<PetSummonResult> future = new CompletableFuture<>();

        // Phase 1: DB Executor reads pet & selection
        dbExecutor.submit(() -> {
            Optional<PetInstance> petOpt = repository.findById(petId);
            if (petOpt.isEmpty()) return new InternalSummonState(false, "Pet veritabanında bulunamadı.", null, null, null);

            PetInstance pet = petOpt.get();
            if (!pet.ownerId().equals(ownerId)) return new InternalSummonState(false, "Bu pet size ait değil.", null, null, null);
            if (pet.availabilityState() == PetAvailabilityState.DISABLED) return new InternalSummonState(false, "Devre dışı bırakılmış petler (DISABLED) çağrılamaz.", null, null, null);

            PetDefinition def = definitionRegistry.find(pet.definitionId()).orElse(null);
            if (def == null) return new InternalSummonState(false, "Pet türü konfigürasyonda bulunamadı: " + pet.definitionId(), null, null, null);

            Optional<PetInstance> currentActive = repository.findActiveByOwner(ownerId);
            UUID previousPetId = currentActive.map(PetInstance::petId).orElse(null);

            return new InternalSummonState(true, null, pet, def, previousPetId);
        }).thenAccept(state -> {
            if (!state.success()) {
                ownerLocks.remove(ownerId, lockMarker);
                future.complete(new PetSummonResult(false, state.message()));
                return;
            }

            // Phase 2: Main Thread pre-event & entity spawn
            mainThreadDispatcher.run(() -> {
                if (!owner.isOnline()) {
                    ownerLocks.remove(ownerId, lockMarker);
                    future.complete(new PetSummonResult(false, "Oyuncu çevrimdışı olduğu için çağırma iptal edildi."));
                    return;
                }

                PetSnapshot snapshot = mapToSnapshot(state.pet(), state.previousPetId() != null);
                PetPreSummonEvent preEvent = new PetPreSummonEvent(owner, snapshot);
                if (Bukkit.getServer() != null) {
                    Bukkit.getPluginManager().callEvent(preEvent);
                }
                if (preEvent.isCancelled()) {
                    ownerLocks.remove(ownerId, lockMarker);
                    future.complete(new PetSummonResult(false, "Pet çağrı işlemi başka bir eklenti tarafından engellendi."));
                    return;
                }

                Entity uncommittedEntity;
                try {
                    uncommittedEntity = coordinator.spawnRuntimeUncommitted(owner, state.pet(), state.definition());
                } catch (Exception e) {
                    ownerLocks.remove(ownerId, lockMarker);
                    future.complete(new PetSummonResult(false, "Pet canlı varlığı oluşturulamadı: " + e.getMessage()));
                    return;
                }

                ActivePet activePet = new ActivePet(state.pet().petId(), ownerId, state.pet().definitionId(), state.pet().level(), uncommittedEntity.getUniqueId(), uncommittedEntity, PetRuntimeState.ACTIVE);

                // Phase 3: DB Executor selection switch transaction
                dbExecutor.submit(() -> {
                    try {
                        selectionRepository.switchSelection(ownerId, state.previousPetId(), state.pet().petId());
                        repository.switchActivePet(ownerId, state.previousPetId(), state.pet().petId());
                        return true;
                    } catch (Exception ex) {
                        if (plugin != null) plugin.getLogger().log(Level.SEVERE, "DB selection switch hatası!", ex);
                        return false;
                    }
                }).thenAccept(dbSuccess -> {
                    mainThreadDispatcher.run(() -> {
                        try {
                            if (!dbSuccess) {
                                coordinator.rollbackRuntimeSpawn(ownerId, uncommittedEntity);
                                future.complete(new PetSummonResult(false, "Veritabanı seçimi güncellenemedi, çağırma geri alındı."));
                                return;
                            }

                            coordinator.commitRuntimeSpawn(activePet);
                            if (profileCache != null) {
                                profileCache.updateSelection(ownerId, state.pet().petId());
                            }

                            PetSnapshot finalSnapshot = mapToSnapshot(state.pet(), true);
                            PetSummonEvent summonEvent = new PetSummonEvent(owner, finalSnapshot, uncommittedEntity);
                            if (Bukkit.getServer() != null) {
                                Bukkit.getPluginManager().callEvent(summonEvent);
                            }

                            future.complete(new PetSummonResult(true, "Pet başarıyla çağırıldı."));
                        } catch (Exception ex) {
                            coordinator.despawnRuntime(ownerId);
                            future.complete(new PetSummonResult(false, "Sistem hatası: " + ex.getMessage()));
                        } finally {
                            ownerLocks.remove(ownerId, lockMarker);
                        }
                    });
                }).exceptionally(ex -> {
                    coordinator.despawnRuntime(ownerId);
                    ownerLocks.remove(ownerId, lockMarker);
                    future.complete(new PetSummonResult(false, "Hata oluştu: " + ex.getMessage()));
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

    public CompletableFuture<PetDismissResult> dismissAsync(Player owner) {
        Objects.requireNonNull(owner, "owner null olamaz.");
        UUID ownerId = owner.getUniqueId();
        CompletableFuture<PetDismissResult> future = new CompletableFuture<>();

        mainThreadDispatcher.run(() -> {
            Optional<ActivePet> activeOpt = coordinator.getRuntimePet(ownerId);
            if (activeOpt.isEmpty()) {
                future.complete(new PetDismissResult(false, "Aktif çağırılmış bir petiniz bulunmuyor."));
                return;
            }

            ActivePet activePet = activeOpt.get();
            PetSnapshot snapshot = new PetSnapshot(activePet.getPetId(), ownerId, activePet.getDefinitionId(), null, activePet.getLevel(), 0, PetAvailabilityState.AVAILABLE, true, true);

            PetPreDismissEvent preEvent = new PetPreDismissEvent(owner, snapshot);
            Bukkit.getPluginManager().callEvent(preEvent);
            if (preEvent.isCancelled()) {
                future.complete(new PetDismissResult(false, "Pet kaldırma işlemi başka bir eklenti tarafından engellendi."));
                return;
            }

            dbExecutor.submit(() -> {
                try {
                    selectionRepository.clear(ownerId);
                    repository.clearActivePet(ownerId);
                    return true;
                } catch (Exception e) {
                    if (plugin != null) plugin.getLogger().log(Level.SEVERE, "DB dismiss clear hatası!", e);
                    return false;
                }
            }).thenAccept(dbSuccess -> {
                mainThreadDispatcher.run(() -> {
                    if (!dbSuccess) {
                        future.complete(new PetDismissResult(false, "Veritabanı seçimi temizlenemedi, pet kaldırılmadı."));
                        return;
                    }

                    if (profileCache != null) {
                        profileCache.clearSelection(ownerId);
                    }

                    coordinator.despawnRuntime(ownerId);

                    PetDismissEvent dismissEvent = new PetDismissEvent(owner, snapshot);
                    Bukkit.getPluginManager().callEvent(dismissEvent);

                    future.complete(new PetDismissResult(true, "Pet başarıyla gönderildi."));
                });
            });
        });

        return future;
    }

    public CompletableFuture<Void> restoreSelectedPetAsync(Player owner) {
        if (owner == null || !owner.isOnline()) return CompletableFuture.completedFuture(null);
        UUID ownerId = owner.getUniqueId();

        return dbExecutor.submit(() -> repository.findActiveByOwner(ownerId)).thenAccept(selectedOpt -> {
            if (selectedOpt.isPresent()) {
                PetInstance pet = selectedOpt.get();
                if (pet.availabilityState() == PetAvailabilityState.DISABLED) return;
                summonAsync(owner, pet.petId());
            }
        });
    }

    private PetSnapshot mapToSnapshot(PetInstance pet, boolean isSelected) {
        return new PetSnapshot(
                pet.petId(),
                pet.ownerId(),
                pet.definitionId(),
                pet.customName(),
                pet.level(),
                pet.experience(),
                pet.availabilityState(),
                isSelected,
                true
        );
    }

    private record InternalSummonState(boolean success, String message, PetInstance pet, PetDefinition definition, UUID previousPetId) {}
}

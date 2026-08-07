package com.petsistemi.application;

import com.petsistemi.api.AsyncPetExperienceService;
import com.petsistemi.api.PetExperienceService;
import com.petsistemi.api.PetSnapshot;
import com.petsistemi.api.event.PetGainExperienceEvent;
import com.petsistemi.api.event.PetLevelUpEvent;
import com.petsistemi.api.result.ExperienceResult;
import com.petsistemi.api.result.LevelResult;
import com.petsistemi.bootstrap.MainThreadDispatcher;
import com.petsistemi.config.RuntimeConfigurationSnapshot;
import com.petsistemi.definition.PetDefinitionRegistry;
import com.petsistemi.progression.ExperienceCurve;
import com.petsistemi.domain.ExperienceSource;
import com.petsistemi.domain.PetDefinition;
import com.petsistemi.domain.PetInstance;
import com.petsistemi.persistence.DatabaseExecutor;
import com.petsistemi.persistence.PetRepository;
import com.petsistemi.persistence.PlayerPetProfileCache;
import com.petsistemi.runtime.ActivePet;
import com.petsistemi.runtime.ActivePetRegistry;
import com.petsistemi.runtime.PetEntityController;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Async experience service with per-pet operation serialization to prevent lost-update races.
 *
 * <p>Stage 9 — XP semantics:
 * <ul>
 *   <li>{@link #removeExperienceAsync}: {@code max(0, currentXp - amount)}</li>
 *   <li>{@link #setExperienceAsync}: direct set; fires event only for increases</li>
 *   <li>{@link #setLevelAsync}: validates bounds, sets XP to curve value, supports level-down</li>
 * </ul>
 *
 * <p>Stage 10 — Lost-update prevention:
 * All four async mutations are serialized per pet via {@code petXpTails} chain.
 * Failures do NOT permanently block future operations.
 */
public class DefaultPetExperienceService implements PetExperienceService, AsyncPetExperienceService {

    private final JavaPlugin plugin;
    private final PetRepository repository;
    private final PetDefinitionRegistry definitionRegistry;
    private final ActivePetRegistry activePetRegistry;
    private final PetEntityController entityController;
    private final ExperienceCurve experienceCurve;
    private final DatabaseExecutor dbExecutor;
    private final MainThreadDispatcher mainThreadDispatcher;
    private final PlayerPetProfileCache profileCache;
    private final AtomicReference<RuntimeConfigurationSnapshot> configSnapshot;

    /** Per-pet XP operation tail for lost-update prevention (Stage 10). */
    private final ConcurrentHashMap<UUID, CompletableFuture<Void>> petXpTails = new ConcurrentHashMap<>();

    // ─────────────────────────────────────────────────────────────────────────
    // CONSTRUCTORS
    // ─────────────────────────────────────────────────────────────────────────

    public DefaultPetExperienceService(JavaPlugin plugin,
                                       PetRepository repository,
                                       PetDefinitionRegistry definitionRegistry,
                                       ActivePetRegistry activePetRegistry,
                                       PetEntityController entityController,
                                       ExperienceCurve experienceCurve,
                                       DatabaseExecutor dbExecutor,
                                       MainThreadDispatcher mainThreadDispatcher,
                                       PlayerPetProfileCache profileCache,
                                       AtomicReference<RuntimeConfigurationSnapshot> configSnapshot) {
        this.plugin = plugin;
        this.repository = repository;
        this.definitionRegistry = definitionRegistry;
        this.activePetRegistry = activePetRegistry;
        this.entityController = entityController;
        this.experienceCurve = experienceCurve != null ? experienceCurve : new com.petsistemi.progression.LinearExperienceCurve(100);
        this.dbExecutor = dbExecutor;
        this.mainThreadDispatcher = mainThreadDispatcher;
        this.profileCache = profileCache;
        this.configSnapshot = configSnapshot;
    }

    public DefaultPetExperienceService(JavaPlugin plugin,
                                       PetRepository repository,
                                       PetDefinitionRegistry definitionRegistry,
                                       ActivePetRegistry activePetRegistry,
                                       PetEntityController entityController,
                                       ExperienceCurve experienceCurve,
                                       DatabaseExecutor dbExecutor) {
        this(plugin, repository, definitionRegistry, activePetRegistry, entityController, experienceCurve, dbExecutor, null, null, null);
    }

    public DefaultPetExperienceService(JavaPlugin plugin,
                                       PetRepository repository,
                                       PetDefinitionRegistry definitionRegistry,
                                       ActivePetRegistry activePetRegistry,
                                       PetEntityController entityController,
                                       ExperienceCurve experienceCurve) {
        this(plugin, repository, definitionRegistry, activePetRegistry, entityController, experienceCurve, null, null, null, null);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ASYNC API
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public CompletableFuture<ExperienceResult> addExperienceAsync(UUID petId, long amount, ExperienceSource source) {
        Objects.requireNonNull(petId, "petId null olamaz.");
        Objects.requireNonNull(source, "source null olamaz.");

        if (amount <= 0) {
            return CompletableFuture.completedFuture(
                    new ExperienceResult(false, "Deneyim miktarı pozitif olmalıdır.", 0, false));
        }

        return serializeXpOp(petId, () ->
                dbExecutor.submit(() -> repository.findById(petId)).thenCompose(petOpt -> {
                    if (petOpt.isEmpty()) {
                        return CompletableFuture.completedFuture(
                                new ExperienceResult(false, "Pet veritabanında bulunamadı.", 0, false));
                    }

                    PetInstance pet = petOpt.get();

                    if (!isProgressionEnabled(pet)) {
                        return CompletableFuture.completedFuture(
                                new ExperienceResult(false, "Bu pet için gelişim sistemi kapalı.", pet.experience(), false));
                    }

                    PetDefinition definition = getDefinition(pet);
                    PetSnapshot snapshot = mapToSnapshot(pet);

                    // Main thread: fire PetGainExperienceEvent
                    CompletableFuture<Long> eventFuture = mainThreadDispatcher != null
                            ? mainThreadDispatcher.supply(() -> {
                                if (Bukkit.getServer() != null) {
                                    PetGainExperienceEvent event = new PetGainExperienceEvent(snapshot, amount, source);
                                    Bukkit.getPluginManager().callEvent(event);
                                    if (event.isCancelled()) return -1L;
                                    return event.getAmount();
                                }
                                return amount;
                            })
                            : CompletableFuture.completedFuture(amount);

                    return eventFuture.thenCompose(actualAmount -> {
                        if (actualAmount <= 0) {
                            return CompletableFuture.completedFuture(
                                    new ExperienceResult(false, "Deneyim kazanma işlemi iptal edildi.", pet.experience(), false));
                        }

                        long safeNewXp;
                        try {
                            safeNewXp = Math.addExact(pet.experience(), actualAmount);
                        } catch (ArithmeticException overflow) {
                            safeNewXp = Long.MAX_VALUE - 1_000_000L;
                        }

                        final long targetXp = safeNewXp;
                        return dbExecutor.submit(() -> computeAndPersistXp(pet, targetXp, definition))
                                .thenCompose(resState -> applyPostDbSuccess(pet, resState));
                    });
                })
        );
    }

    /**
     * Stage 9: Decreases XP by {@code amount} (clamped to 0). Uses the amount parameter.
     */
    @Override
    public CompletableFuture<ExperienceResult> removeExperienceAsync(UUID petId, long amount) {
        Objects.requireNonNull(petId, "petId null olamaz.");
        if (amount < 0) {
            return CompletableFuture.completedFuture(
                    new ExperienceResult(false, "Kaldırılacak XP miktarı negatif olamaz.", 0, false));
        }

        return serializeXpOp(petId, () ->
                dbExecutor.submit(() -> repository.findById(petId)).thenCompose(petOpt -> {
                    if (petOpt.isEmpty()) {
                        return CompletableFuture.completedFuture(
                                new ExperienceResult(false, "Pet veritabanında bulunamadı.", 0, false));
                    }
                    PetInstance pet = petOpt.get();
                    if (!isProgressionEnabled(pet)) {
                        return CompletableFuture.completedFuture(
                                new ExperienceResult(false, "Bu pet için gelişim sistemi kapalı.", pet.experience(), false));
                    }
                    PetDefinition definition = getDefinition(pet);
                    long targetXp = Math.max(0L, pet.experience() - amount);
                    return dbExecutor.submit(() -> computeAndPersistXp(pet, targetXp, definition))
                            .thenCompose(resState -> applyPostDbSuccess(pet, resState));
                })
        );
    }

    /**
     * Stage 9: Sets XP directly to {@code amount}. Fires PetGainExperienceEvent only for increases.
     * Always writes to DB if value differs.
     */
    @Override
    public CompletableFuture<ExperienceResult> setExperienceAsync(UUID petId, long amount, ExperienceSource source) {
        Objects.requireNonNull(petId, "petId null olamaz.");
        if (amount < 0) {
            return CompletableFuture.completedFuture(
                    new ExperienceResult(false, "XP miktarı negatif olamaz.", 0, false));
        }

        return serializeXpOp(petId, () ->
                dbExecutor.submit(() -> repository.findById(petId)).thenCompose(petOpt -> {
                    if (petOpt.isEmpty()) {
                        return CompletableFuture.completedFuture(
                                new ExperienceResult(false, "Pet veritabanında bulunamadı.", 0, false));
                    }
                    PetInstance pet = petOpt.get();
                    if (!isProgressionEnabled(pet)) {
                        return CompletableFuture.completedFuture(
                                new ExperienceResult(false, "Bu pet için gelişim sistemi kapalı.", pet.experience(), false));
                    }

                    // Idempotent: same value → no-op success
                    if (pet.experience() == amount) {
                        return CompletableFuture.completedFuture(
                                new ExperienceResult(true, "Deneyim değişmedi (aynı değer).", pet.experience(), false));
                    }

                    boolean isIncrease = amount > pet.experience();
                    PetDefinition definition = getDefinition(pet);

                    if (isIncrease && mainThreadDispatcher != null) {
                        PetSnapshot snapshot = mapToSnapshot(pet);
                        long delta = amount - pet.experience();
                        CompletableFuture<Long> eventFuture = mainThreadDispatcher.supply(() -> {
                            if (Bukkit.getServer() != null) {
                                PetGainExperienceEvent event = new PetGainExperienceEvent(snapshot, delta, source);
                                Bukkit.getPluginManager().callEvent(event);
                                if (event.isCancelled()) return -1L;
                                // Use event's final amount to determine new target
                                return pet.experience() + event.getAmount();
                            }
                            return amount;
                        });
                        return eventFuture.thenCompose(finalAmount -> {
                            if (finalAmount < 0) {
                                return CompletableFuture.completedFuture(
                                        new ExperienceResult(false, "XP ayarlama işlemi iptal edildi.", pet.experience(), false));
                            }
                            return dbExecutor.submit(() -> computeAndPersistXp(pet, finalAmount, definition))
                                    .thenCompose(resState -> applyPostDbSuccess(pet, resState));
                        });
                    } else {
                        // Decrease or no dispatcher: write directly
                        return dbExecutor.submit(() -> computeAndPersistXp(pet, amount, definition))
                                .thenCompose(resState -> applyPostDbSuccess(pet, resState));
                    }
                })
        );
    }

    /**
     * Stage 9: Sets level. Validates bounds. Supports level-down. Sets XP to curve value.
     */
    @Override
    public CompletableFuture<LevelResult> setLevelAsync(UUID petId, int level) {
        Objects.requireNonNull(petId, "petId null olamaz.");
        if (level < 1) {
            return CompletableFuture.completedFuture(
                    new LevelResult(false, "Level 1'den küçük olamaz.", 0));
        }

        return serializeXpOp(petId, () ->
                dbExecutor.submit(() -> repository.findById(petId)).thenCompose(petOpt -> {
                    if (petOpt.isEmpty()) {
                        return CompletableFuture.completedFuture(
                                new LevelResult(false, "Pet veritabanında bulunamadı.", 0));
                    }
                    PetInstance pet = petOpt.get();
                    PetDefinition definition = getDefinition(pet);

                    int maxLevel = getMaxLevel(pet, definition);
                    if (level > maxLevel) {
                        return CompletableFuture.completedFuture(
                                new LevelResult(false, "Level " + maxLevel + " maksimum değerini aşamaz.", pet.level()));
                    }

                    long targetXp = requiredExperienceForLevel(level);
                    return dbExecutor.submit(() -> computeAndPersistXp(pet, targetXp, definition))
                            .thenCompose(resState -> {
                                if (!resState.success()) {
                                    return CompletableFuture.completedFuture(
                                            new LevelResult(false, resState.message(), pet.level()));
                                }
                                return applyPostDbSuccess(pet, resState)
                                        .thenApply(xpResult -> new LevelResult(true, "Level ayarlandı.", resState.newLevel()));
                            });
                })
        );
    }

    @Override
    public long requiredExperienceForLevel(int level) {
        return experienceCurve.getRequiredExperience(level);
    }

    public int calculateLevelFromXp(long xp) {
        return experienceCurve.getLevelForExperience(xp);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // STAGE 10: PER-PET SERIALIZATION
    // ─────────────────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private <T> CompletableFuture<T> serializeXpOp(UUID petId, Callable<CompletableFuture<T>> op) {
        CompletableFuture<Void> prevTail = petXpTails.getOrDefault(petId, CompletableFuture.completedFuture(null));
        CompletableFuture<T> result = new CompletableFuture<>();

        CompletableFuture<Void> newTail = prevTail.thenCompose(ignored -> {
            try {
                return op.call().whenComplete((res, ex) -> {
                    if (ex != null) result.completeExceptionally(ex);
                    else result.complete(res);
                }).thenApply(r -> (Void) null);
            } catch (Exception e) {
                result.completeExceptionally(e);
                return CompletableFuture.completedFuture(null);
            }
        }).exceptionally(ex -> null); // failures do NOT block future ops

        // CAS: only update tail if it's still the one we chained from
        petXpTails.merge(petId, newTail, (old, n) -> old == prevTail ? n : old);
        // Clean up when done
        newTail.whenComplete((v, ex) -> petXpTails.remove(petId, newTail));

        return result;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PRIVATE HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    /** Computes new level from targetXp and persists to DB. Runs on DB thread. */
    private DbXpState computeAndPersistXp(PetInstance pet, long targetXp, PetDefinition definition) {
        int maxLevel = getMaxLevel(pet, definition);

        int newLevel = calculateLevelFromXp(targetXp);
        if (newLevel > maxLevel) {
            newLevel = maxLevel;
            targetXp = requiredExperienceForLevel(maxLevel);
        }
        if (newLevel < 1) newLevel = 1;

        boolean leveledUp = newLevel > pet.level();
        boolean leveledDown = newLevel < pet.level();
        PetInstance updatedPet = pet.withLevelAndExperience(newLevel, targetXp);

        try {
            repository.update(updatedPet);
            return new DbXpState(true, null, updatedPet, newLevel, targetXp, leveledUp, leveledDown);
        } catch (Exception e) {
            return new DbXpState(false, "Deneyim veritabanına kaydedilemedi: " + e.getMessage(),
                    pet, pet.level(), pet.experience(), false, false);
        }
    }

    /** After successful DB write: update cache + main-thread nameplate + level event. */
    private CompletableFuture<ExperienceResult> applyPostDbSuccess(PetInstance oldPet, DbXpState resState) {
        if (!resState.success()) {
            return CompletableFuture.completedFuture(
                    new ExperienceResult(false, resState.message(), oldPet.experience(), false));
        }

        if (profileCache != null) {
            profileCache.updateExperience(oldPet.ownerId(), oldPet.petId(), resState.newLevel(), resState.newXp());
        }

        Runnable mainAction = () -> {
            Optional<ActivePet> activeOpt = activePetRegistry != null
                    ? activePetRegistry.getByOwner(oldPet.ownerId()) : Optional.empty();
            if (activeOpt.isPresent()) {
                ActivePet active = activeOpt.get();
                PetDefinition def = getDefinition(resState.updatedPet());
                if (active.getPetId().equals(oldPet.petId()) && entityController != null && def != null) {
                    entityController.updateName(active.getSpawnedEntity(), resState.updatedPet(), def);
                }
            }

            if (resState.leveledUp() && Bukkit.getServer() != null) {
                PetLevelUpEvent levelUpEvent = new PetLevelUpEvent(
                        mapToSnapshot(resState.updatedPet()), oldPet.level(), resState.newLevel());
                Bukkit.getPluginManager().callEvent(levelUpEvent);

                PetDefinition def = getDefinition(resState.updatedPet());
                if (def != null && def.levelRewards() != null) {
                    org.bukkit.entity.Player owner = Bukkit.getPlayer(oldPet.ownerId());
                    for (com.petsistemi.domain.PetLevelRewardDefinition reward : def.levelRewards()) {
                        if (resState.newLevel() >= reward.level() && oldPet.level() < reward.level()) {
                            if (reward.message() != null && owner != null && owner.isOnline()) {
                                owner.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(reward.message()));
                            }
                            if (reward.commands() != null) {
                                for (String cmd : reward.commands()) {
                                    String formattedCmd = cmd.replace("{player}", owner != null ? owner.getName() : "");
                                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), formattedCmd);
                                }
                            }
                        }
                    }
                }
            }
        };

        CompletableFuture<Void> mainFuture = mainThreadDispatcher != null
                ? mainThreadDispatcher.run(mainAction)
                : CompletableFuture.runAsync(mainAction);

        return mainFuture.thenApply(v ->
                new ExperienceResult(true, "Deneyim başarıyla güncellendi.", resState.newXp(), resState.leveledUp()));
    }

    private boolean isProgressionEnabled(PetInstance pet) {
        // Check definition flag first
        if (definitionRegistry != null) {
            PetDefinition def = definitionRegistry.find(pet.definitionId()).orElse(null);
            if (def != null && !def.progressionEnabled()) return false;
        }
        // Check global config
        if (configSnapshot != null) {
            RuntimeConfigurationSnapshot snap = configSnapshot.get();
            if (snap != null && snap.configuration() != null) {
                return snap.configuration().progression().enabled();
            }
        }
        return true; // default: enabled
    }

    private PetDefinition getDefinition(PetInstance pet) {
        return definitionRegistry != null ? definitionRegistry.find(pet.definitionId()).orElse(null) : null;
    }

    private int getMaxLevel(PetInstance pet, PetDefinition definition) {
        if (definition != null && definition.maxLevel() > 0) return definition.maxLevel();
        if (configSnapshot != null) {
            RuntimeConfigurationSnapshot snap = configSnapshot.get();
            if (snap != null && snap.configuration() != null) {
                int configMax = snap.configuration().progression().maximumLevel();
                if (configMax > 0) return configMax;
            }
        }
        return 100; // last-resort fallback
    }

    private PetSnapshot mapToSnapshot(PetInstance pet) {
        return new PetSnapshot(pet.petId(), pet.ownerId(), pet.definitionId(), pet.customName(),
                pet.level(), pet.experience(), pet.availabilityState(), false, false);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DEPRECATED SYNCHRONOUS METHODS
    // ─────────────────────────────────────────────────────────────────────────

    @Deprecated @Override public ExperienceResult addExperience(UUID petId, long amount, ExperienceSource source) { return addExperienceAsync(petId, amount, source).join(); }
    @Deprecated @Override public ExperienceResult removeExperience(UUID petId, long amount) { return removeExperienceAsync(petId, amount).join(); }
    @Deprecated @Override public ExperienceResult setExperience(UUID petId, long amount, ExperienceSource source) { return setExperienceAsync(petId, amount, source).join(); }
    @Deprecated @Override public LevelResult setLevel(UUID petId, int level) { return setLevelAsync(petId, level).join(); }

    private record DbXpState(boolean success, String message, PetInstance updatedPet,
                              int newLevel, long newXp, boolean leveledUp, boolean leveledDown) {}
}

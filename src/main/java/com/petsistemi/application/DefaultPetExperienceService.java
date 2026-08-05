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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

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

    // --- ASYNC API ---

    @Override
    public CompletableFuture<ExperienceResult> addExperienceAsync(UUID petId, long amount, ExperienceSource source) {
        Objects.requireNonNull(petId, "petId null olamaz.");
        Objects.requireNonNull(source, "source null olamaz.");

        if (amount <= 0) {
            return CompletableFuture.completedFuture(new ExperienceResult(false, "Deneyim miktarı pozitif olmalıdır.", 0, false));
        }

        return dbExecutor.submit(() -> repository.findById(petId)).thenCompose(petOpt -> {
            if (petOpt.isEmpty()) {
                return CompletableFuture.completedFuture(new ExperienceResult(false, "Pet veritabanında bulunamadı.", 0, false));
            }

            PetInstance pet = petOpt.get();
            PetDefinition definition = definitionRegistry != null ? definitionRegistry.find(pet.definitionId()).orElse(null) : null;
            if (definition != null && !definition.progressionEnabled()) {
                return CompletableFuture.completedFuture(new ExperienceResult(false, "Bu pet türü için gelişim sistemi kapalı.", pet.experience(), false));
            }

            PetSnapshot snapshot = mapToSnapshot(pet);

            // Main Thread Event
            CompletableFuture<Long> eventFuture = mainThreadDispatcher != null ? mainThreadDispatcher.supply(() -> {
                if (Bukkit.getServer() != null) {
                    PetGainExperienceEvent event = new PetGainExperienceEvent(snapshot, amount, source);
                    Bukkit.getPluginManager().callEvent(event);
                    if (event.isCancelled()) return -1L;
                    return event.getAmount();
                }
                return amount;
            }) : CompletableFuture.completedFuture(amount);

            return eventFuture.thenCompose(actualAmount -> {
                if (actualAmount <= 0) {
                    return CompletableFuture.completedFuture(new ExperienceResult(false, "Deneyim kazanma işlemi iptal edildi veya miktar sıfır kaldı.", pet.experience(), false));
                }

                // DB Executor Write
                return dbExecutor.submit(() -> computeAndPersistExperienceDb(pet, actualAmount, definition)).thenCompose(resState -> {
                    if (!resState.success()) {
                        return CompletableFuture.completedFuture(new ExperienceResult(false, resState.message(), pet.experience(), false));
                    }

                    if (profileCache != null) {
                        profileCache.updateExperience(pet.ownerId(), petId, resState.newLevel(), resState.newXp());
                    }

                    // Main Thread Nameplate & LevelUp Event
                    Runnable mainAction = () -> {
                        Optional<ActivePet> activeOpt = activePetRegistry != null ? activePetRegistry.getByOwner(pet.ownerId()) : Optional.empty();
                        if (activeOpt.isPresent()) {
                            ActivePet active = activeOpt.get();
                            if (active.getPetId().equals(petId) && entityController != null && definition != null) {
                                entityController.updateName(active.getSpawnedEntity(), resState.updatedPet(), definition);
                            }
                        }

                        if (resState.leveledUp() && Bukkit.getServer() != null) {
                            PetLevelUpEvent levelUpEvent = new PetLevelUpEvent(mapToSnapshot(resState.updatedPet()), pet.level(), resState.newLevel());
                            Bukkit.getPluginManager().callEvent(levelUpEvent);
                        }
                    };

                    CompletableFuture<Void> mainFuture = mainThreadDispatcher != null ? mainThreadDispatcher.run(mainAction) : CompletableFuture.runAsync(mainAction);
                    return mainFuture.thenApply(v -> new ExperienceResult(true, "Deneyim başarıyla eklendi.", resState.newXp(), resState.leveledUp()));
                });
            });
        });
    }

    @Override
    public CompletableFuture<ExperienceResult> removeExperienceAsync(UUID petId, long amount) {
        return setExperienceAsync(petId, 0, ExperienceSource.COMMAND);
    }

    @Override
    public CompletableFuture<ExperienceResult> setExperienceAsync(UUID petId, long amount, ExperienceSource source) {
        Objects.requireNonNull(petId, "petId null olamaz.");
        return dbExecutor.submit(() -> repository.findById(petId)).thenCompose(petOpt -> {
            if (petOpt.isEmpty()) return CompletableFuture.completedFuture(new ExperienceResult(false, "Pet bulunamadı.", 0, false));
            PetInstance pet = petOpt.get();
            long delta = amount - pet.experience();
            if (delta <= 0) return CompletableFuture.completedFuture(new ExperienceResult(true, "Deneyim güncellendi.", pet.experience(), false));
            return addExperienceAsync(petId, delta, source);
        });
    }

    @Override
    public CompletableFuture<LevelResult> setLevelAsync(UUID petId, int level) {
        Objects.requireNonNull(petId, "petId null olamaz.");
        long targetXp = requiredExperienceForLevel(level);
        return setExperienceAsync(petId, targetXp, ExperienceSource.ADMIN).thenApply(res ->
                new LevelResult(res.success(), res.message(), level)
        );
    }

    @Override
    public long requiredExperienceForLevel(int level) {
        return experienceCurve.getRequiredExperience(level);
    }

    // --- PRIVATE DB-ONLY COMPUTATION (NO BUKKIT API) ---

    private DbXpState computeAndPersistExperienceDb(PetInstance pet, long actualAmount, PetDefinition definition) {
        int maxLevel = definition != null ? definition.maxLevel() : 100;
        long newXp;
        try {
            newXp = Math.addExact(pet.experience(), actualAmount);
        } catch (ArithmeticException e) {
            newXp = Long.MAX_VALUE;
        }

        int newLevel = calculateLevelFromXp(newXp);
        if (newLevel > maxLevel) {
            newLevel = maxLevel;
            newXp = requiredExperienceForLevel(maxLevel);
        }

        boolean leveledUp = newLevel > pet.level();
        PetInstance updatedPet = pet.withLevelAndExperience(newLevel, newXp);

        try {
            repository.update(updatedPet);
            return new DbXpState(true, null, updatedPet, newLevel, newXp, leveledUp);
        } catch (Exception e) {
            return new DbXpState(false, "Deneyim veritabanına kaydedilemedi: " + e.getMessage(), pet, pet.level(), pet.experience(), false);
        }
    }

    public int calculateLevelFromXp(long xp) {
        return experienceCurve.getLevelForExperience(xp);
    }

    private PetSnapshot mapToSnapshot(PetInstance pet) {
        return new PetSnapshot(pet.petId(), pet.ownerId(), pet.definitionId(), pet.customName(), pet.level(), pet.experience(), pet.availabilityState(), false, false);
    }

    // --- DEPRECATED SYNCHRONOUS METHODS ---

    @Deprecated @Override public ExperienceResult addExperience(UUID petId, long amount, ExperienceSource source) { return addExperienceAsync(petId, amount, source).join(); }
    @Deprecated @Override public ExperienceResult removeExperience(UUID petId, long amount) { return removeExperienceAsync(petId, amount).join(); }
    @Deprecated @Override public ExperienceResult setExperience(UUID petId, long amount, ExperienceSource source) { return setExperienceAsync(petId, amount, source).join(); }
    @Deprecated @Override public LevelResult setLevel(UUID petId, int level) { return setLevelAsync(petId, level).join(); }

    private record DbXpState(boolean success, String message, PetInstance updatedPet, int newLevel, long newXp, boolean leveledUp) {}
}

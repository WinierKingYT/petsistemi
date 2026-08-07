package com.petsistemi.application;

import com.petsistemi.api.AsyncPetService;
import com.petsistemi.api.PetService;
import com.petsistemi.api.PetSnapshot;
import com.petsistemi.api.event.PetGiveEvent;
import com.petsistemi.api.event.PetRenameEvent;
import com.petsistemi.api.result.*;
import com.petsistemi.bootstrap.MainThreadDispatcher;
import com.petsistemi.config.PluginConfiguration;
import com.petsistemi.config.RuntimeConfigurationSnapshot;
import com.petsistemi.definition.PetDefinitionRegistry;
import com.petsistemi.domain.PetAvailabilityState;
import com.petsistemi.domain.PetDefinition;
import com.petsistemi.domain.PetInstance;
import com.petsistemi.domain.PetSelection;
import com.petsistemi.persistence.DatabaseExecutor;
import com.petsistemi.persistence.PetRepository;
import com.petsistemi.persistence.PetSelectionRepository;
import com.petsistemi.persistence.PlayerPetProfile;
import com.petsistemi.persistence.PlayerPetProfileCache;
import com.petsistemi.runtime.ActivePet;
import com.petsistemi.runtime.ActivePetRegistry;
import com.petsistemi.runtime.PetEntityController;
import com.petsistemi.runtime.PetRuntimeCoordinator;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

public class DefaultPetService implements PetService, AsyncPetService {

    private final JavaPlugin plugin;
    private final PetRepository repository;
    private final PetSelectionRepository selectionRepository;
    private final PetDefinitionRegistry definitionRegistry;
    private final ActivePetRegistry activePetRegistry;
    private final PetEntityController entityController;
    private final DatabaseExecutor dbExecutor;
    private final MainThreadDispatcher mainThreadDispatcher;
    private final PlayerPetProfileCache profileCache;
    private final AtomicReference<RuntimeConfigurationSnapshot> configSnapshot;
    private final PetRuntimeCoordinator coordinator;

    public DefaultPetService(JavaPlugin plugin,
                             PetRepository repository,
                             PetSelectionRepository selectionRepository,
                             PetDefinitionRegistry definitionRegistry,
                             ActivePetRegistry activePetRegistry,
                             PetEntityController entityController,
                             DatabaseExecutor dbExecutor,
                             MainThreadDispatcher mainThreadDispatcher,
                             PlayerPetProfileCache profileCache,
                             AtomicReference<RuntimeConfigurationSnapshot> configSnapshot,
                             PetRuntimeCoordinator coordinator) {
        this.plugin = plugin;
        this.repository = repository;
        this.selectionRepository = selectionRepository;
        this.definitionRegistry = definitionRegistry;
        this.activePetRegistry = activePetRegistry;
        this.entityController = entityController;
        this.dbExecutor = dbExecutor;
        this.mainThreadDispatcher = mainThreadDispatcher;
        this.profileCache = profileCache;
        this.configSnapshot = configSnapshot;
        this.coordinator = coordinator;
    }

    public DefaultPetService(JavaPlugin plugin,
                             PetRepository repository,
                             PetSelectionRepository selectionRepository,
                             PetDefinitionRegistry definitionRegistry,
                             ActivePetRegistry activePetRegistry,
                             PetEntityController entityController,
                             DatabaseExecutor dbExecutor,
                             MainThreadDispatcher mainThreadDispatcher,
                             PlayerPetProfileCache profileCache,
                             AtomicReference<RuntimeConfigurationSnapshot> configSnapshot) {
        this(plugin, repository, selectionRepository, definitionRegistry, activePetRegistry, entityController, dbExecutor, mainThreadDispatcher, profileCache, configSnapshot, null);
    }

    public DefaultPetService(JavaPlugin plugin,
                             PetRepository repository,
                             PetSelectionRepository selectionRepository,
                             PetDefinitionRegistry definitionRegistry,
                             ActivePetRegistry activePetRegistry,
                             PetEntityController entityController,
                             DatabaseExecutor dbExecutor) {
        this(plugin, repository, selectionRepository, definitionRegistry, activePetRegistry, entityController, dbExecutor, null, null, null, null);
    }

    // --- ASYNC API ---

    @Override
    public CompletableFuture<Optional<PetSnapshot>> findPetAsync(UUID petId) {
        Objects.requireNonNull(petId, "petId null olamaz.");
        return dbExecutor.submit(() -> repository.findById(petId)).thenApply(opt -> opt.map(this::mapToSnapshot));
    }

    @Override
    public CompletableFuture<Collection<PetSnapshot>> getOwnedPetsAsync(UUID ownerId) {
        return getOwnedPetsAsyncList(ownerId).thenApply(list -> list);
    }

    public CompletableFuture<List<PetSnapshot>> getOwnedPetsAsyncList(UUID ownerId) {
        Objects.requireNonNull(ownerId, "ownerId null olamaz.");
        if (profileCache != null) {
            Optional<PlayerPetProfile> cached = profileCache.getProfile(ownerId);
            if (cached.isPresent()) {
                return CompletableFuture.completedFuture(new ArrayList<>(cached.get().pets().values()));
            }
            return profileCache.loadProfileAsync(dbExecutor, ownerId).thenApply(profile -> new ArrayList<>(profile.pets().values()));
        }
        return dbExecutor.submit(() -> repository.findByOwner(ownerId)).thenApply(list -> list.stream().map(this::mapToSnapshot).toList());
    }

    @Override
    public CompletableFuture<Optional<PetSnapshot>> getSelectedPetAsync(UUID ownerId) {
        Objects.requireNonNull(ownerId, "ownerId null olamaz.");
        if (profileCache != null) {
            Optional<PlayerPetProfile> cached = profileCache.getProfile(ownerId);
            if (cached.isPresent()) {
                UUID selectedId = cached.get().selectedPetId();
                if (selectedId == null) return CompletableFuture.completedFuture(Optional.empty());
                return CompletableFuture.completedFuture(Optional.ofNullable(cached.get().pets().get(selectedId)));
            }
        }
        return dbExecutor.submit(() -> repository.findActiveByOwner(ownerId)).thenApply(opt -> opt.map(this::mapToSnapshot));
    }

    @Override
    public CompletableFuture<PetGiveResult> givePetAsync(UUID ownerId, String definitionId) {
        Objects.requireNonNull(ownerId, "ownerId null olamaz.");
        Objects.requireNonNull(definitionId, "definitionId null olamaz.");

        PetDefinition definition = definitionRegistry != null ? definitionRegistry.find(definitionId).orElse(null) : null;
        if (definition == null) {
            return CompletableFuture.completedFuture(new PetGiveResult(false, "Geçersiz pet türü: " + definitionId, null));
        }

        int maxPets = (configSnapshot != null && configSnapshot.get() != null && configSnapshot.get().configuration() != null)
                ? configSnapshot.get().configuration().limits().maximumOwnedPets() : 5;

        Player onlinePlayer = Bukkit.getPlayer(ownerId);
        if (onlinePlayer != null) {
            for (int i = 50; i >= 1; i--) {
                if (onlinePlayer.hasPermission("petsistemi.limit." + i) || onlinePlayer.hasPermission("companionpets.limit." + i)) {
                    maxPets = i;
                    break;
                }
            }
        }

        final int finalMaxPets = maxPets;
        return dbExecutor.submit(() -> insertPetDb(ownerId, definition, finalMaxPets)).thenCompose(state -> {
            if (!state.success()) {
                return CompletableFuture.completedFuture(new PetGiveResult(false, state.message(), null));
            }
            PetSnapshot snapshot = mapToSnapshot(state.pet());
            if (profileCache != null) {
                profileCache.updatePet(ownerId, snapshot);
            }

            Runnable eventRunnable = () -> {
                if (Bukkit.getServer() != null) {
                    Bukkit.getPluginManager().callEvent(new PetGiveEvent(snapshot));
                }
            };
            CompletableFuture<Void> mainFuture = mainThreadDispatcher != null ? mainThreadDispatcher.run(eventRunnable) : CompletableFuture.runAsync(eventRunnable);
            return mainFuture.thenApply(v -> new PetGiveResult(true, "Pet başarıyla verildi.", snapshot));
        });
    }

    /**
     * Admin-only rename: no ownership check.
     * @see #renameAsync(UUID, UUID, String) for player-facing rename with ownership validation.
     */
    @Override
    public CompletableFuture<PetRenameResult> renameAsync(UUID petId, String newName) {
        return renameAsAdminAsync(petId, newName);
    }

    /**
     * Admin-only rename. No ownership check — caller must have verified admin permission.
     */
    public CompletableFuture<PetRenameResult> renameAsAdminAsync(UUID petId, String newName) {
        Objects.requireNonNull(petId, "petId null olamaz.");
        Objects.requireNonNull(newName, "newName null olamaz.");

        String validatedName = validateNameInput(newName);
        if (validatedName == null) {
            return CompletableFuture.completedFuture(new PetRenameResult(false, "İsim konfigürasyondaki sınırlar veya yasaklı kelimeler nedeniyle geçersiz."));
        }

        return dbExecutor.submit(() -> loadPetForRenameDb(petId)).thenCompose(petOpt -> {
            if (petOpt.isEmpty()) {
                return CompletableFuture.completedFuture(new PetRenameResult(false, "Pet veritabanında bulunamadı."));
            }
            return executeRenameInternal(petOpt.get(), petId, validatedName);
        });
    }

    /** Player-facing rename: requires non-null ownerId and validates ownership. */
    @Override
    public CompletableFuture<PetRenameResult> renameAsync(UUID ownerId, UUID petId, String newName) {
        Objects.requireNonNull(ownerId, "ownerId null olamaz — kullanıcı rename için owner kimliği gereklidir.");
        Objects.requireNonNull(petId, "petId null olamaz.");
        Objects.requireNonNull(newName, "newName null olamaz.");

        String validatedName = validateNameInput(newName);
        if (validatedName == null) {
            return CompletableFuture.completedFuture(new PetRenameResult(false, "İsim konfigürasyondaki sınırlar veya yasaklı kelimeler nedeniyle geçersiz."));
        }

        return dbExecutor.submit(() -> loadPetForRenameDb(petId)).thenCompose(petOpt -> {
            if (petOpt.isEmpty()) {
                return CompletableFuture.completedFuture(new PetRenameResult(false, "Pet veritabanında bulunamadı."));
            }
            PetInstance pet = petOpt.get();

            // Stage 11: ownership validation
            if (!pet.ownerId().equals(ownerId)) {
                return CompletableFuture.completedFuture(new PetRenameResult(false, "Bu pet size ait değil."));
            }

            return executeRenameInternal(pet, petId, validatedName);
        });
    }

    private CompletableFuture<PetRenameResult> executeRenameInternal(PetInstance pet, UUID petId, String validatedName) {
        CompletableFuture<PetRenameEvent> eventFuture = mainThreadDispatcher != null ? mainThreadDispatcher.supply(() -> {
            PetSnapshot beforeSnapshot = mapToSnapshot(pet);
            Player onlinePlayer = Bukkit.getServer() != null ? Bukkit.getPlayer(pet.ownerId()) : null;
            PetRenameEvent event = new PetRenameEvent(onlinePlayer, pet.ownerId(), beforeSnapshot, beforeSnapshot.customName(), validatedName);
            if (Bukkit.getServer() != null) {
                Bukkit.getPluginManager().callEvent(event);
            }
            return event;
        }) : CompletableFuture.completedFuture(new PetRenameEvent(null, pet.ownerId(), mapToSnapshot(pet), pet.customName(), validatedName));

        return eventFuture.thenCompose(event -> {
            if (event.isCancelled()) {
                return CompletableFuture.completedFuture(new PetRenameResult(false, "İsim değiştirme işlemi başka bir eklenti tarafından iptal edildi."));
            }

            // Stage 11: re-validate the event-modified final name before persisting
            String finalName = validateNameInput(event.getNewName());
            if (finalName == null) {
                return CompletableFuture.completedFuture(new PetRenameResult(false, "Olay tarafından belirlenen son isim geçersiz."));
            }
            return dbExecutor.submit(() -> updatePetNameDb(pet, finalName)).thenCompose(updatedPet -> {
                if (profileCache != null) {
                    profileCache.updateName(pet.ownerId(), petId, finalName);
                }

                Runnable nameplateRunnable = () -> {
                    if (activePetRegistry != null) {
                        Optional<ActivePet> activeOpt = activePetRegistry.getByOwner(pet.ownerId());
                        if (activeOpt.isPresent()) {
                            ActivePet active = activeOpt.get();
                            if (active.getPetId().equals(petId) && entityController != null && definitionRegistry != null) {
                                PetDefinition def = definitionRegistry.find(pet.definitionId()).orElse(null);
                                if (def != null) {
                                    entityController.updateName(active.getSpawnedEntity(), updatedPet, def);
                                }
                            }
                        }
                    }
                };

                CompletableFuture<Void> mainFuture = mainThreadDispatcher != null ? mainThreadDispatcher.run(nameplateRunnable) : CompletableFuture.runAsync(nameplateRunnable);
                return mainFuture.thenApply(v -> new PetRenameResult(true, "Pet ismi başarıyla değiştirildi."));
            });
        });
    }

    @Override
    public CompletableFuture<PetDisableResult> disablePetAsync(UUID petId) {
        Objects.requireNonNull(petId, "petId null olamaz.");

        return dbExecutor.submit(() -> disablePetDb(petId)).thenCompose(pet -> {
            if (profileCache != null) {
                profileCache.updateAvailability(pet.ownerId(), petId, PetAvailabilityState.DISABLED);
            }

            // Stage 18: use coordinator.despawnRuntime instead of manual registry+entityController
            Runnable cleanupRunnable = () -> {
                if (activePetRegistry != null) {
                    Optional<ActivePet> activeOpt = activePetRegistry.getByOwner(pet.ownerId());
                    if (activeOpt.isPresent() && activeOpt.get().getPetId().equals(petId)) {
                        if (coordinator != null) {
                            coordinator.despawnRuntime(pet.ownerId());
                        } else {
                            // Fallback if coordinator not injected
                            activePetRegistry.unregister(pet.ownerId());
                            if (entityController != null && activeOpt.get().getSpawnedEntity() != null) {
                                entityController.remove(activeOpt.get().getSpawnedEntity());
                            }
                        }
                    }
                }
            };
            CompletableFuture<Void> mainFuture = mainThreadDispatcher != null ? mainThreadDispatcher.run(cleanupRunnable) : CompletableFuture.runAsync(cleanupRunnable);
            return mainFuture.thenApply(v -> new PetDisableResult(true, "Pet başarıyla devre dışı bırakıldı."));
        }).exceptionally(ex -> new PetDisableResult(false, "Devre dışı bırakma hatası: " + ex.getMessage()));
    }

    @Override
    public CompletableFuture<PetDisableResult> enablePetAsync(UUID petId) {
        Objects.requireNonNull(petId, "petId null olamaz.");

        return dbExecutor.submit(() -> enablePetDb(petId)).thenApply(pet -> {
            if (profileCache != null) {
                profileCache.updateAvailability(pet.ownerId(), petId, PetAvailabilityState.AVAILABLE);
            }
            return new PetDisableResult(true, "Pet başarıyla etkinleştirildi.");
        });
    }

    @Override
    public CompletableFuture<PetRemoveResult> removePetAsync(UUID petId) {
        Objects.requireNonNull(petId, "petId null olamaz.");

        return dbExecutor.submit(() -> removePetDb(petId)).thenCompose(pet -> {
            if (profileCache != null) {
                profileCache.removePet(pet.ownerId(), petId);
            }

            // Stage 18: central lifecycle cleanup via coordinator
            Runnable cleanupRunnable = () -> {
                if (activePetRegistry != null) {
                    Optional<ActivePet> activeOpt = activePetRegistry.getByOwner(pet.ownerId());
                    if (activeOpt.isPresent() && activeOpt.get().getPetId().equals(petId)) {
                        if (coordinator != null) {
                            coordinator.despawnRuntime(pet.ownerId());
                        } else {
                            // Fallback if coordinator not injected
                            activePetRegistry.unregister(pet.ownerId());
                            if (entityController != null && activeOpt.get().getSpawnedEntity() != null) {
                                entityController.remove(activeOpt.get().getSpawnedEntity());
                            }
                        }
                    }
                }
            };
            CompletableFuture<Void> mainFuture = mainThreadDispatcher != null ? mainThreadDispatcher.run(cleanupRunnable) : CompletableFuture.runAsync(cleanupRunnable);
            return mainFuture.thenApply(v -> new PetRemoveResult(true, "Pet başarıyla silindi."));
        }).exceptionally(ex -> new PetRemoveResult(false, "Silme hatası: " + ex.getMessage()));
    }

    // --- PRIVATE DB-ONLY METHODS ---

    private DbInsertState insertPetDb(UUID ownerId, PetDefinition definition, int maxPets) {
        List<PetInstance> existingPets = repository.findByOwner(ownerId);
        if (existingPets.size() >= maxPets) {
            return new DbInsertState(false, "Maksimum pet sınırına ulaştınız (" + maxPets + ").", null);
        }

        long now = System.currentTimeMillis();
        PetInstance newPet = new PetInstance(
                UUID.randomUUID(),
                ownerId,
                definition.id(),
                definition.displayName(),
                1,
                0L,
                PetAvailabilityState.AVAILABLE,
                now,
                now
        );

        repository.insert(newPet);
        return new DbInsertState(true, null, newPet);
    }

    private Optional<PetInstance> loadPetForRenameDb(UUID petId) {
        return repository.findById(petId);
    }

    private PetInstance updatePetNameDb(PetInstance pet, String newName) {
        PetInstance updated = pet.withCustomName(newName);
        repository.update(updated);
        return updated;
    }

    private PetInstance disablePetDb(UUID petId) {
        PetInstance pet = repository.findById(petId).orElseThrow(() -> new IllegalArgumentException("Pet bulunamadı."));
        PetInstance updated = pet.withAvailabilityState(PetAvailabilityState.DISABLED);
        // Determine if this pet is currently selected — only clear selection if it is.
        Optional<PetSelection> selectionOpt = selectionRepository.findByOwner(pet.ownerId());
        UUID selectedOwnerId = (selectionOpt.isPresent() && selectionOpt.get().petId().equals(petId))
                ? pet.ownerId() : null;
        // Atomic: clear selection (if selected) + update state in one JDBC transaction.
        repository.disablePetTransactional(selectedOwnerId, updated);
        return updated;
    }

    private PetInstance enablePetDb(UUID petId) {
        PetInstance pet = repository.findById(petId).orElseThrow(() -> new IllegalArgumentException("Pet bulunamadı."));
        PetInstance updated = pet.withAvailabilityState(PetAvailabilityState.AVAILABLE);
        repository.update(updated);
        return updated;
    }

    private PetInstance removePetDb(UUID petId) {
        PetInstance pet = repository.findById(petId).orElseThrow(() -> new IllegalArgumentException("Pet bulunamadı."));
        // Determine if this pet is currently selected — only clear selection if it is.
        Optional<PetSelection> selectionOpt = selectionRepository.findByOwner(pet.ownerId());
        UUID selectedOwnerId = (selectionOpt.isPresent() && selectionOpt.get().petId().equals(petId))
                ? pet.ownerId() : null;
        // Atomic: clear selection (if selected) + delete in one JDBC transaction.
        repository.removePetTransactional(selectedOwnerId, petId);
        return pet;
    }

    /**
     * Validates a pet name against the typed configuration.
     * <ul>
     *   <li>Length checks before/after trim</li>
     *   <li>Newline and carriage-return ban</li>
     *   <li>{@code &} and {@code §} color/formatting controls gated by {@code allow-colors}/{@code allow-formatting}</li>
     *   <li>{@code <} and {@code >} tag controls gated by {@code allow-formatting}</li>
     *   <li>Allowed character set: Unicode letters/digits/space/hyphen/underscore/apostrophe</li>
     * </ul>
     * Returns the trimmed, validated name or {@code null} when invalid.
     */
    private String validateNameInput(String name) {
        if (name == null || name.isBlank()) return null;
        if (name.indexOf('\n') >= 0 || name.indexOf('\r') >= 0) return null;

        PluginConfiguration config = configSnapshot != null && configSnapshot.get() != null
                ? configSnapshot.get().configuration() : null;
        int minLen = config != null ? config.naming().minimumLength() : 2;
        int maxLen = config != null ? config.naming().maximumLength() : 16;
        boolean allowColors = config != null && config.naming().allowColors();
        boolean allowFormatting = config != null && config.naming().allowFormatting();

        // Pre-trim length must not exceed the configured maximum (whitespace included)
        if (name.length() > maxLen) return null;
        String trimmed = name.trim();
        if (trimmed.length() < minLen) return null;

        // Color codes: &/§ followed by a color code char
        if (!allowColors) {
            for (int i = 0; i < trimmed.length() - 1; i++) {
                char c = trimmed.charAt(i);
                if (c == '&' || c == '§') {
                    char code = trimmed.charAt(i + 1);
                    if ("0123456789abcdefABCDEF".indexOf(code) >= 0) return null;
                }
            }
        }
        // Formatting codes: &/§ followed by a format char
        if (!allowFormatting) {
            for (int i = 0; i < trimmed.length() - 1; i++) {
                char c = trimmed.charAt(i);
                if (c == '&' || c == '§') {
                    char code = trimmed.charAt(i + 1);
                    if ("klmnorKLMNOR".indexOf(code) >= 0) return null;
                }
            }
            // Angle-bracket tags (e.g. <bold>) require formatting to be enabled
            if (trimmed.indexOf('<') >= 0 || trimmed.indexOf('>') >= 0) return null;
        }

        // Allowed character set
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (Character.isLetterOrDigit(c) || c == ' ' || c == '-' || c == '_' || c == '\'') continue;
            if ((c == '&' || c == '§') && (allowColors || allowFormatting)) continue;
            return null;
        }
        return trimmed;
    }

    private PetSnapshot mapToSnapshot(PetInstance pet) {
        boolean isSpawned = activePetRegistry != null && activePetRegistry.getByOwner(pet.ownerId())
                .map(a -> a.getPetId().equals(pet.petId())).orElse(false);
        return new PetSnapshot(
                pet.petId(),
                pet.ownerId(),
                pet.definitionId(),
                pet.customName(),
                pet.level(),
                pet.experience(),
                pet.availabilityState(),
                false,
                isSpawned
        );
    }

    // --- DEPRECATED SYNCHRONOUS API METHODS & PETSERVICE IMPLEMENTATION ---

    @Override
    public Optional<PetSnapshot> getSpawnedPet(UUID ownerId) {
        if (activePetRegistry == null || ownerId == null) return Optional.empty();
        return activePetRegistry.getByOwner(ownerId).map(active ->
                new PetSnapshot(active.getPetId(), active.getOwnerId(), active.getDefinitionId(), null, active.getLevel(), 0, PetAvailabilityState.AVAILABLE, true, true)
        );
    }

    @Deprecated @Override public Optional<PetSnapshot> getActivePet(UUID ownerId) { return getSpawnedPet(ownerId); }
    @Deprecated @Override public Optional<PetSnapshot> findPet(UUID petId) { return findPetAsync(petId).join(); }
    @Deprecated @Override public Collection<PetSnapshot> getOwnedPets(UUID ownerId) { return getOwnedPetsAsync(ownerId).join(); }
    @Deprecated @Override public Optional<PetSnapshot> getSelectedPet(UUID ownerId) { return getSelectedPetAsync(ownerId).join(); }
    @Deprecated @Override public PetGiveResult givePet(UUID ownerId, String definitionId) { return givePetAsync(ownerId, definitionId).join(); }
    @Deprecated @Override public PetSummonResult summon(Player owner, UUID petId) { return new PetSummonResult(false, "Async API kullanın."); }
    @Deprecated @Override public PetDismissResult dismiss(Player owner) { return new PetDismissResult(false, "Async API kullanın."); }
    @Deprecated @Override public PetRenameResult rename(Player owner, UUID petId, String newName) { return renameAsync(owner.getUniqueId(), petId, newName).join(); }
    @Deprecated @Override public PetRenameResult rename(UUID ownerId, UUID petId, String newName) { return renameAsync(ownerId, petId, newName).join(); }
    @Deprecated @Override public PetDisableResult disablePet(UUID petId) { return disablePetAsync(petId).join(); }
    @Deprecated @Override public PetDisableResult enablePet(UUID petId) { return enablePetAsync(petId).join(); }
    @Deprecated @Override public PetRemoveResult removePet(UUID petId) { return removePetAsync(petId).join(); }

    private record DbInsertState(boolean success, String message, PetInstance pet) {}
}

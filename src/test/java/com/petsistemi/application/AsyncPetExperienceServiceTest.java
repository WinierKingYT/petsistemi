package com.petsistemi.application;

import com.petsistemi.api.PetSnapshot;
import com.petsistemi.api.result.ExperienceResult;
import com.petsistemi.definition.PetDefinitionRegistry;
import com.petsistemi.domain.ExperienceSource;
import com.petsistemi.domain.PetAvailabilityState;
import com.petsistemi.domain.PetDefinition;
import com.petsistemi.domain.PetInstance;
import com.petsistemi.domain.PetSelection;
import com.petsistemi.persistence.DatabaseExecutor;
import com.petsistemi.persistence.PetRepository;
import com.petsistemi.persistence.PetSelectionRepository;
import com.petsistemi.persistence.PlayerPetProfile;
import com.petsistemi.persistence.PlayerPetProfileCache;
import com.petsistemi.progression.LinearExperienceCurve;
import com.petsistemi.runtime.ActivePetRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class AsyncPetExperienceServiceTest {

    private DatabaseExecutor dbExecutor;
    private PlayerPetProfileCache profileCache;
    private DefaultPetExperienceService experienceService;
    private UUID ownerId;
    private UUID petId;
    private boolean dbShouldFail = false;

    @BeforeEach
    void setUp() {
        dbExecutor = new DatabaseExecutor(Logger.getLogger("TestAsyncPetExperienceService"));
        ownerId = UUID.randomUUID();
        petId = UUID.randomUUID();

        PetRepository repository = new PetRepository() {
            private PetInstance instance = new PetInstance(petId, ownerId, "wolf", "Kurt", 1, 0, PetAvailabilityState.AVAILABLE, System.currentTimeMillis(), System.currentTimeMillis());

            @Override public Optional<PetInstance> findById(UUID id) { return id.equals(petId) ? Optional.ofNullable(instance) : Optional.empty(); }
            @Override public List<PetInstance> findByOwner(UUID id) { return id.equals(ownerId) && instance != null ? List.of(instance) : List.of(); }
            @Override public void insert(PetInstance pet) { this.instance = pet; }
            @Override
            public void update(PetInstance pet) {
                if (dbShouldFail) {
                    throw new RuntimeException("Database connection failure simulation");
                }
                this.instance = pet;
            }
            @Override public void delete(UUID id) { if (id.equals(petId)) instance = null; }
            @Override public Optional<PetInstance> findActiveByOwner(UUID ownerId) { return Optional.empty(); }
            @Override public void setActivePet(UUID ownerId, UUID petId) {}
            @Override public void clearActivePet(UUID ownerId) {}
            @Override public void switchActivePet(UUID ownerId, UUID oldPetId, UUID newPetId) {}
            @Override public void restoreActivePet(UUID ownerId, UUID oldPetId, UUID newPetId) {}
            @Override public void clearActivePetAndSetAvailable(UUID ownerId, UUID petId) {}
        };

        PetSelectionRepository selectionRepository = new PetSelectionRepository() {
            @Override public Optional<PetSelection> findByOwner(UUID id) { return Optional.empty(); }
            @Override public void select(UUID ownerId, UUID petId) {}
            @Override public void clear(UUID ownerId) {}
            @Override public void switchSelection(UUID ownerId, UUID previousPetId, UUID newPetId) {}
        };

        profileCache = new PlayerPetProfileCache(repository, selectionRepository);
        PetSnapshot snapshot = new PetSnapshot(petId, ownerId, "wolf", "Kurt", 1, 0, PetAvailabilityState.AVAILABLE, false, false);
        profileCache.putLoadedProfile(new PlayerPetProfile(ownerId, Map.of(petId, snapshot), null, System.currentTimeMillis(), 1L));

        PetDefinition def = new PetDefinition("wolf", "Kurt", List.of("WOLF"), "DOG", true, true, true, true, true, true, 100, true, List.of());
        PetDefinitionRegistry definitionRegistry = new PetDefinitionRegistry() {
            @Override public Optional<PetDefinition> find(String id) { return Optional.of(def); }
            @Override public Collection<PetDefinition> getAll() { return List.of(def); }
            @Override public void reload() {}
        };

        ActivePetRegistry activePetRegistry = new ActivePetRegistry();

        experienceService = new DefaultPetExperienceService(
                null,
                repository,
                definitionRegistry,
                activePetRegistry,
                null,
                new LinearExperienceCurve(100),
                dbExecutor
        );
    }

    @AfterEach
    void tearDown() {
        if (dbExecutor != null && !dbExecutor.isClosed()) {
            dbExecutor.close();
        }
    }

    @Test
    void testAddExperienceAsyncUpdatesXpAndLevel() throws Exception {
        CompletableFuture<ExperienceResult> future = experienceService.addExperienceAsync(petId, 150L, ExperienceSource.MOB_KILL);
        ExperienceResult result = future.get(5, TimeUnit.SECONDS);

        assertTrue(result.success());
        assertEquals(150L, result.newExperience());
        assertTrue(result.leveledUp());
    }

    @Test
    void testDbFailureDoesNotCorruptState() throws Exception {
        dbShouldFail = true;
        CompletableFuture<ExperienceResult> future = experienceService.addExperienceAsync(petId, 100L, ExperienceSource.MOB_KILL);
        ExperienceResult result = future.get(5, TimeUnit.SECONDS);

        assertFalse(result.success());
        assertTrue(result.message().contains("Deneyim veritabanına kaydedilemedi"));
    }
}

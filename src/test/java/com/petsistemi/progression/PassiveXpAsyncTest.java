package com.petsistemi.progression;

import com.petsistemi.api.AsyncPetExperienceService;
import com.petsistemi.api.PetExperienceService;
import com.petsistemi.api.result.ExperienceResult;
import com.petsistemi.api.result.LevelResult;
import com.petsistemi.domain.ExperienceSource;
import com.petsistemi.domain.PetRuntimeState;
import com.petsistemi.runtime.ActivePet;
import com.petsistemi.runtime.ActivePetRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class PassiveXpAsyncTest {

    private ActivePetRegistry activePetRegistry;
    private UUID pet1Id;
    private UUID pet2Id;

    private interface MockExperienceService extends PetExperienceService, AsyncPetExperienceService {}

    @BeforeEach
    void setUp() {
        activePetRegistry = new ActivePetRegistry();
        pet1Id = UUID.randomUUID();
        pet2Id = UUID.randomUUID();

        activePetRegistry.register(new ActivePet(pet1Id, UUID.randomUUID(), "wolf", 1, UUID.randomUUID(), null, PetRuntimeState.ACTIVE));
        activePetRegistry.register(new ActivePet(pet2Id, UUID.randomUUID(), "cat", 1, UUID.randomUUID(), null, PetRuntimeState.ACTIVE));
    }

    @Test
    void testFailureOnOnePetDoesNotBlockOtherPets() throws Exception {
        AtomicInteger successCount = new AtomicInteger(0);

        MockExperienceService mockService = new MockExperienceService() {
            @Override
            public CompletableFuture<ExperienceResult> addExperienceAsync(UUID petId, long amount, ExperienceSource source) {
                if (petId.equals(pet1Id)) {
                    CompletableFuture<ExperienceResult> failed = new CompletableFuture<>();
                    failed.completeExceptionally(new RuntimeException("Simulated DB Failure for Pet 1"));
                    return failed;
                } else {
                    successCount.incrementAndGet();
                    return CompletableFuture.completedFuture(new ExperienceResult(true, "Success", 100, false));
                }
            }

            @Override public ExperienceResult addExperience(UUID petId, long amount, ExperienceSource source) { return null; }
            @Override public ExperienceResult removeExperience(UUID petId, long amount) { return null; }
            @Override public ExperienceResult setExperience(UUID petId, long amount, ExperienceSource source) { return null; }
            @Override public LevelResult setLevel(UUID petId, int level) { return null; }
            @Override public long requiredExperienceForLevel(int level) { return 0; }

            @Override public CompletableFuture<ExperienceResult> removeExperienceAsync(UUID petId, long amount) { return null; }
            @Override public CompletableFuture<ExperienceResult> setExperienceAsync(UUID petId, long amount, ExperienceSource source) { return null; }
            @Override public CompletableFuture<LevelResult> setLevelAsync(UUID petId, int level) { return null; }
        };

        PetPassiveXpTask task = new PetPassiveXpTask(activePetRegistry, mockService);
        task.run();

        assertEquals(1, successCount.get(), "Pet 1 hatası Pet 2'nin tecrübe kazanmasını engellememelidir.");
    }

    @Test
    void testDuplicatePendingOperationDeduplicated() {
        AtomicInteger invocationCount = new AtomicInteger(0);

        MockExperienceService mockService = new MockExperienceService() {
            @Override
            public CompletableFuture<ExperienceResult> addExperienceAsync(UUID petId, long amount, ExperienceSource source) {
                invocationCount.incrementAndGet();
                CompletableFuture<ExperienceResult> pendingFuture = new CompletableFuture<>();
                // Leave future uncompleted to simulate pending DB operation
                return pendingFuture;
            }

            @Override public ExperienceResult addExperience(UUID petId, long amount, ExperienceSource source) { return null; }
            @Override public ExperienceResult removeExperience(UUID petId, long amount) { return null; }
            @Override public ExperienceResult setExperience(UUID petId, long amount, ExperienceSource source) { return null; }
            @Override public LevelResult setLevel(UUID petId, int level) { return null; }
            @Override public long requiredExperienceForLevel(int level) { return 0; }

            @Override public CompletableFuture<ExperienceResult> removeExperienceAsync(UUID petId, long amount) { return null; }
            @Override public CompletableFuture<ExperienceResult> setExperienceAsync(UUID petId, long amount, ExperienceSource source) { return null; }
            @Override public CompletableFuture<LevelResult> setLevelAsync(UUID petId, int level) { return null; }
        };

        PetPassiveXpTask task = new PetPassiveXpTask(activePetRegistry, mockService);
        task.run(); // First tick starts pending future for pet 1 & 2
        assertEquals(2, invocationCount.get());

        task.run(); // Second tick while futures are still pending -> should skip duplicate operations
        assertEquals(2, invocationCount.get(), "Pending görev tamamlanmadan yeni işlem başlatılmamalıdır.");
    }
}

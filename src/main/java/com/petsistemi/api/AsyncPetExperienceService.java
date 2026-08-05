package com.petsistemi.api;

import com.petsistemi.api.result.ExperienceResult;
import com.petsistemi.api.result.LevelResult;
import com.petsistemi.domain.ExperienceSource;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface AsyncPetExperienceService {
    CompletableFuture<ExperienceResult> addExperienceAsync(UUID petId, long amount, ExperienceSource source);
    CompletableFuture<ExperienceResult> removeExperienceAsync(UUID petId, long amount);
    CompletableFuture<ExperienceResult> setExperienceAsync(UUID petId, long amount, ExperienceSource source);
    CompletableFuture<LevelResult> setLevelAsync(UUID petId, int level);
}

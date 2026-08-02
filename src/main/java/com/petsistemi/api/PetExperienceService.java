package com.petsistemi.api;

import com.petsistemi.api.result.*;
import com.petsistemi.domain.ExperienceSource;
import java.util.UUID;

public interface PetExperienceService {

    ExperienceResult addExperience(UUID petId, long amount, ExperienceSource source);

    ExperienceResult removeExperience(UUID petId, long amount);

    ExperienceResult setExperience(UUID petId, long amount, ExperienceSource source);

    LevelResult setLevel(UUID petId, int level);

    long requiredExperienceForLevel(int level);
}

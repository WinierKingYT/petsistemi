package com.petsistemi.api.result;

import com.petsistemi.api.PetSnapshot;

public record PetEvolutionResult(
        boolean success,
        String message,
        PetSnapshot before,
        PetSnapshot after
) {}

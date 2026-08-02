package com.petsistemi.api.result;

import com.petsistemi.api.PetSnapshot;

public record PetGiveResult(
        boolean success,
        String message,
        PetSnapshot petSnapshot
) {}

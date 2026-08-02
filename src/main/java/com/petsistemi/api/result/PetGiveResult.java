package com.petsistemi.api.result;

import com.petsistemi.domain.PetInstance;

public record PetGiveResult(boolean success, String message, PetInstance petInstance) {
}

package com.petsistemi.runtime;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PetRecoveryQueue {

    private final Map<UUID, Integer> recoveryAttempts = new ConcurrentHashMap<>();

    public int incrementAttempt(UUID petId) {
        return recoveryAttempts.merge(petId, 1, Integer::sum);
    }

    public int getAttempt(UUID petId) {
        return recoveryAttempts.getOrDefault(petId, 0);
    }

    public void clearAttempt(UUID petId) {
        recoveryAttempts.remove(petId);
    }

    public void clearAll() {
        recoveryAttempts.clear();
    }
}

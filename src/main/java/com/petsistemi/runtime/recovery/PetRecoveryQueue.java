package com.petsistemi.runtime.recovery;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PetRecoveryQueue {

    private final Set<UUID> pendingRecoveries = ConcurrentHashMap.newKeySet();

    public boolean tryStart(UUID ownerId, UUID petId) {
        if (petId == null) return false;
        return pendingRecoveries.add(petId);
    }

    public boolean isPending(UUID petId) {
        if (petId == null) return false;
        return pendingRecoveries.contains(petId);
    }

    public void clear(UUID petId) {
        if (petId != null) {
            pendingRecoveries.remove(petId);
        }
    }

    public void clearAll() {
        pendingRecoveries.clear();
    }
}

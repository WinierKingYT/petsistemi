package com.petsistemi.runtime.recovery;

import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PetRecoveryQueue {

    private static final int MAX_ATTEMPTS = 3;
    private static final long[] BACKOFF_TICKS = {20L, 60L, 200L};

    private final Map<UUID, RecoveryAttempt> pendingRecoveries = new ConcurrentHashMap<>();

    public record RecoveryAttempt(UUID ownerId, UUID petId, int attemptCount, long nextScheduledTick) {}

    public boolean shouldAttemptRecovery(UUID petId, long currentTick) {
        RecoveryAttempt attempt = pendingRecoveries.get(petId);
        if (attempt == null) return true;
        return attempt.attemptCount < MAX_ATTEMPTS && currentTick >= attempt.nextScheduledTick;
    }

    public void recordAttempt(UUID ownerId, UUID petId, long currentTick) {
        RecoveryAttempt existing = pendingRecoveries.get(petId);
        int nextCount = (existing != null ? existing.attemptCount : 0) + 1;
        long delay = nextCount <= BACKOFF_TICKS.length ? BACKOFF_TICKS[nextCount - 1] : 200L;
        pendingRecoveries.put(petId, new RecoveryAttempt(ownerId, petId, nextCount, currentTick + delay));
    }

    public boolean isExhausted(UUID petId) {
        RecoveryAttempt attempt = pendingRecoveries.get(petId);
        return attempt != null && attempt.attemptCount >= MAX_ATTEMPTS;
    }

    public void clear(UUID petId) {
        pendingRecoveries.remove(petId);
    }

    public void clearAll() {
        pendingRecoveries.clear();
    }
}

package com.petsistemi.gui;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages player chat input sessions (such as pet renaming) with automatic 60-second expiration.
 */
public class PlayerInputSessionManager {

    private static final long SESSION_TIMEOUT_MS = 60_000L; // 60 seconds

    private record SessionData(UUID petId, long startTime) {}

    private final Map<UUID, SessionData> activeRenameSessions = new ConcurrentHashMap<>();

    public void startRenameSession(UUID playerId, UUID petId) {
        if (playerId == null || petId == null) return;
        activeRenameSessions.put(playerId, new SessionData(petId, System.currentTimeMillis()));
    }

    public boolean hasActiveSession(UUID playerId) {
        if (playerId == null) return false;
        SessionData data = activeRenameSessions.get(playerId);
        if (data == null) return false;

        if (System.currentTimeMillis() - data.startTime() > SESSION_TIMEOUT_MS) {
            activeRenameSessions.remove(playerId);
            return false;
        }
        return true;
    }

    public UUID getTargetPetId(UUID playerId) {
        if (playerId == null) return null;
        if (!hasActiveSession(playerId)) return null;
        SessionData data = activeRenameSessions.get(playerId);
        return data != null ? data.petId() : null;
    }

    public void removeSession(UUID playerId) {
        if (playerId != null) {
            activeRenameSessions.remove(playerId);
        }
    }

    public void clearAll() {
        activeRenameSessions.clear();
    }
}

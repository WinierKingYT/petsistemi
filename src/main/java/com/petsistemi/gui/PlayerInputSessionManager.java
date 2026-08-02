package com.petsistemi.gui;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerInputSessionManager {

    private final Map<UUID, UUID> activeRenameSessions = new ConcurrentHashMap<>();

    public void startRenameSession(UUID playerId, UUID petId) {
        activeRenameSessions.put(playerId, petId);
    }

    public boolean hasActiveSession(UUID playerId) {
        return activeRenameSessions.containsKey(playerId);
    }

    public UUID getTargetPetId(UUID playerId) {
        return activeRenameSessions.get(playerId);
    }

    public void removeSession(UUID playerId) {
        activeRenameSessions.remove(playerId);
    }

    public void clearAll() {
        activeRenameSessions.clear();
    }
}

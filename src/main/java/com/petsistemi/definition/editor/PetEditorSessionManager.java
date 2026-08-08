package com.petsistemi.definition.editor;

import org.bukkit.configuration.InvalidConfigurationException;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Per-administrator editor drafts and pending chat fields. */
public final class PetEditorSessionManager {

    private static final long TIMEOUT_MS = 10 * 60_000L;
    private final PetDefinitionEditorService service;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();

    public PetEditorSessionManager(PetDefinitionEditorService service) {
        this.service = service;
    }

    public PetDefinitionEditorService.Draft begin(UUID playerId, String definitionId)
            throws IOException, InvalidConfigurationException {
        PetDefinitionEditorService.Draft draft = service.open(definitionId);
        sessions.put(playerId, new Session(draft, null, System.currentTimeMillis()));
        return draft;
    }

    public Optional<PetDefinitionEditorService.Draft> draft(UUID playerId) {
        Session session = liveSession(playerId);
        return session == null ? Optional.empty() : Optional.of(session.draft());
    }

    public void await(UUID playerId, PetEditorField field) {
        Session session = liveSession(playerId);
        if (session != null) sessions.put(playerId, new Session(session.draft(), field, System.currentTimeMillis()));
    }

    public Optional<PetEditorField> awaiting(UUID playerId) {
        Session session = liveSession(playerId);
        return session == null ? Optional.empty() : Optional.ofNullable(session.awaiting());
    }

    public boolean applyAwaited(UUID playerId, String value) {
        Session session = liveSession(playerId);
        if (session == null || session.awaiting() == null) return false;
        session.draft().set(session.awaiting(), value);
        sessions.put(playerId, new Session(session.draft(), null, System.currentTimeMillis()));
        return true;
    }

    public PetDefinitionEditorService.Validation validate(UUID playerId) {
        return service.validate(draft(playerId).orElse(null));
    }

    public PetDefinitionEditorService.SaveResult save(UUID playerId) {
        return service.save(draft(playerId).orElse(null));
    }

    public void discard(UUID playerId) { sessions.remove(playerId); }

    private Session liveSession(UUID playerId) {
        Session session = sessions.get(playerId);
        if (session != null && System.currentTimeMillis() - session.touchedAt() > TIMEOUT_MS) {
            sessions.remove(playerId);
            return null;
        }
        return session;
    }

    private record Session(PetDefinitionEditorService.Draft draft, PetEditorField awaiting, long touchedAt) {}
}

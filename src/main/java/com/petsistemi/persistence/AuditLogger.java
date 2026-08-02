package com.petsistemi.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;
import java.util.logging.Logger;

public class AuditLogger {

    private final ConnectionProvider connectionProvider;
    private final Logger logger;

    public AuditLogger(ConnectionProvider connectionProvider, Logger logger) {
        this.connectionProvider = connectionProvider;
        this.logger = logger;
    }

    public void log(String actorType, String actorId, String action, UUID ownerId, UUID petId, String detailsJson, boolean success) {
        String sql = "INSERT INTO pet_audit_log (timestamp, actor_type, actor_id, action, owner_id, pet_id, details_json, success) VALUES (?, ?, ?, ?, ?, ?, ?, ?);";
        try (PreparedStatement ps = connectionProvider.getConnection().prepareStatement(sql)) {
            ps.setLong(1, System.currentTimeMillis());
            ps.setString(2, actorType);
            ps.setString(3, actorId);
            ps.setString(4, action);
            ps.setString(5, ownerId != null ? ownerId.toString() : null);
            ps.setString(6, petId != null ? petId.toString() : null);
            ps.setString(7, detailsJson);
            ps.setInt(8, success ? 1 : 0);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.severe("Audit log kaydedilemedi: " + e.getMessage());
        }
    }
}

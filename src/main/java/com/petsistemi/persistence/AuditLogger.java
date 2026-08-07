package com.petsistemi.persistence;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.logging.Logger;

public class AuditLogger {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ConnectionProvider connectionProvider;
    private final Logger logger;
    private final File auditLogFile;

    public AuditLogger(ConnectionProvider connectionProvider, Logger logger) {
        this(connectionProvider, logger, null);
    }

    public AuditLogger(ConnectionProvider connectionProvider, Logger logger, File dataFolder) {
        this.connectionProvider = connectionProvider;
        this.logger = logger;
        if (dataFolder != null) {
            File logsDir = new File(dataFolder, "logs");
            if (!logsDir.exists()) logsDir.mkdirs();
            this.auditLogFile = new File(logsDir, "pet_audit.log");
        } else {
            this.auditLogFile = null;
        }
    }

    public void log(String actorType, String actorId, String action, UUID ownerId, UUID petId, String detailsJson, boolean success) {
        // 1. Database log
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
            if (logger != null) {
                logger.severe("Audit log veritabanına kaydedilemedi: " + e.getMessage());
            }
        }

        // 2. File log
        if (auditLogFile != null) {
            try (PrintWriter out = new PrintWriter(new FileWriter(auditLogFile, true))) {
                String timestamp = LocalDateTime.now().format(DATE_FORMATTER);
                out.printf("[%s] [%s:%s] ACTION: %s | Owner: %s | Pet: %s | Details: %s | Success: %b%n",
                        timestamp, actorType, actorId, action,
                        ownerId != null ? ownerId : "NONE",
                        petId != null ? petId : "NONE",
                        detailsJson != null ? detailsJson : "",
                        success);
            } catch (Exception ignored) {}
        }
    }

    public void logAction(String action, String actorId, UUID ownerId, UUID petId, String details) {
        log("ADMIN", actorId, action, ownerId, petId, details, true);
    }
}

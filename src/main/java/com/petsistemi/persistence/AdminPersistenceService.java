package com.petsistemi.persistence;

import com.petsistemi.persistence.migration.MigrationBackupManager;

import java.io.File;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * Admin-only persistence operations (health checks, backups) that run on the
 * database executor thread — never on the Bukkit main thread.
 */
public class AdminPersistenceService {

    private final DatabaseExecutor dbExecutor;
    private final ConnectionProvider connectionProvider;
    private final Logger logger;

    public AdminPersistenceService(DatabaseExecutor dbExecutor, ConnectionProvider connectionProvider, Logger logger) {
        this.dbExecutor = Objects.requireNonNull(dbExecutor, "dbExecutor null olamaz.");
        this.connectionProvider = Objects.requireNonNull(connectionProvider, "connectionProvider null olamaz.");
        this.logger = Objects.requireNonNull(logger, "logger null olamaz.");
    }

    public record DatabaseHealthReport(boolean ok, String integrity, boolean fkClean, String errorMessage) {}

    public CompletableFuture<DatabaseHealthReport> checkHealthAsync() {
        return dbExecutor.submit(() -> {
            try (Connection conn = connectionProvider.getConnection();
                 Statement stmt = conn.createStatement()) {

                String integrity = "Bilinmiyor";
                try (ResultSet rs = stmt.executeQuery("PRAGMA integrity_check;")) {
                    if (rs.next()) integrity = rs.getString(1);
                }

                boolean fkClean = true;
                try (ResultSet rs = stmt.executeQuery("PRAGMA foreign_key_check;")) {
                    if (rs.next()) fkClean = false;
                }

                return new DatabaseHealthReport(true, integrity, fkClean, null);
            } catch (Exception e) {
                logger.severe("Veritabanı sağlık kontrolü hatası: " + e.getMessage());
                return new DatabaseHealthReport(false, null, false, e.getMessage());
            }
        });
    }

    public CompletableFuture<File> createBackupAsync(File dbFile, File backupDir, int maxBackups) {
        return dbExecutor.submit(() -> {
            MigrationBackupManager backupManager = new MigrationBackupManager(logger);
            try (Connection conn = connectionProvider.getConnection()) {
                return backupManager.createBackup(conn, dbFile, backupDir, 0, true, true, maxBackups);
            } catch (Exception e) {
                logger.severe("Yedek alma hatası: " + e.getMessage());
                throw new PetPersistenceException("Veritabanı yedeği alınamadı.", e);
            }
        });
    }
}

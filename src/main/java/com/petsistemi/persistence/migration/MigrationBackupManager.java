package com.petsistemi.persistence.migration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Comparator;
import java.util.logging.Logger;

public class MigrationBackupManager {

    private final Logger logger;

    public MigrationBackupManager(Logger logger) {
        this.logger = logger;
    }

    public File createBackup(File dbFile, File backupDir, int targetVersion, boolean enabled, boolean failOnError, int maxBackups) throws IOException {
        return createBackup(null, dbFile, backupDir, targetVersion, enabled, failOnError, maxBackups);
    }

    public File createBackup(Connection connection, File dbFile, File backupDir, int targetVersion, boolean enabled, boolean failOnError, int maxBackups) throws IOException {
        if (!enabled || dbFile == null || !dbFile.exists() || dbFile.length() == 0) {
            return null;
        }

        if (!backupDir.exists()) {
            backupDir.mkdirs();
        }

        // 1. Flush WAL log to main database file if connection is active
        if (connection != null) {
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("PRAGMA wal_checkpoint(TRUNCATE);");
            } catch (Exception e) {
                logger.warning("WAL checkpoint gerçekleştirilemedi: " + e.getMessage());
            }
        }

        String timestampStr = String.valueOf(System.currentTimeMillis());
        String backupName = String.format("database-before-v%d-%s.db", targetVersion, timestampStr);
        File backupFile = new File(backupDir, backupName);

        try {
            // Copy main .db file
            Files.copy(dbFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

            // Copy -wal and -shm files if present
            File walFile = new File(dbFile.getParentFile(), dbFile.getName() + "-wal");
            if (walFile.exists() && walFile.length() > 0) {
                File backupWal = new File(backupDir, backupName + "-wal");
                Files.copy(walFile.toPath(), backupWal.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }

            File shmFile = new File(dbFile.getParentFile(), dbFile.getName() + "-shm");
            if (shmFile.exists() && shmFile.length() > 0) {
                File backupShm = new File(backupDir, backupName + "-shm");
                Files.copy(shmFile.toPath(), backupShm.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }

            // 2. Perform Integrity and Foreign Key Checks on the backup file
            verifyBackupIntegrity(backupFile);

            logger.info("WAL-safe veritabanı yedeği alındı ve doğrulandı: " + backupFile.getName());
            cleanOldBackups(backupDir, maxBackups);
            return backupFile;
        } catch (Exception e) {
            logger.severe("Veritabanı yedeği alınırken/doğrulanırken hata oluştu: " + e.getMessage());
            if (backupFile.exists()) {
                backupFile.delete();
            }
            if (failOnError) {
                if (e instanceof IOException ioEx) throw ioEx;
                throw new IOException("Veritabanı yedeği doğrulaması başarısız oldu: " + e.getMessage(), e);
            }
            return null;
        }
    }

    private void verifyBackupIntegrity(File backupFile) throws Exception {
        String jdbcUrl = "jdbc:sqlite:" + backupFile.getAbsolutePath();
        try (Connection conn = DriverManager.getConnection(jdbcUrl);
             Statement stmt = conn.createStatement()) {

            // Integrity Check
            try (ResultSet rs = stmt.executeQuery("PRAGMA integrity_check;")) {
                if (rs.next()) {
                    String result = rs.getString(1);
                    if (!"ok".equalsIgnoreCase(result)) {
                        throw new IllegalStateException("Yedek veritabanı bozuk! Integrity check: " + result);
                    }
                }
            }

            // Foreign Key Check
            try (ResultSet rs = stmt.executeQuery("PRAGMA foreign_key_check;")) {
                if (rs.next()) {
                    String table = rs.getString(1);
                    String rowid = rs.getString(2);
                    String parent = rs.getString(3);
                    throw new IllegalStateException("Yedek veritabanında yabancı anahtar ihlali tespit edildi! Tablo: " + table + ", RowID: " + rowid + ", Parent: " + parent);
                }
            }
        }
    }

    private void cleanOldBackups(File backupDir, int maxBackups) {
        if (maxBackups <= 0) return;
        File[] files = backupDir.listFiles((dir, name) -> name.startsWith("database-before-v") && name.endsWith(".db"));
        if (files == null || files.length <= maxBackups) return;

        Arrays.sort(files, Comparator.comparingLong(File::lastModified));
        int toDelete = files.length - maxBackups;
        for (int i = 0; i < toDelete; i++) {
            if (files[i].delete()) {
                logger.info("Eski veritabanı yedeği temizlendi: " + files[i].getName());
            }
            File walFile = new File(backupDir, files[i].getName() + "-wal");
            if (walFile.exists()) walFile.delete();
            File shmFile = new File(backupDir, files[i].getName() + "-shm");
            if (shmFile.exists()) shmFile.delete();
        }
    }
}

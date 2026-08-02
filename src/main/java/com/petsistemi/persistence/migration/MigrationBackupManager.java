package com.petsistemi.persistence.migration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Comparator;
import java.util.logging.Logger;

public class MigrationBackupManager {

    private final Logger logger;

    public MigrationBackupManager(Logger logger) {
        this.logger = logger;
    }

    public File createBackup(File dbFile, File backupDir, int targetVersion, boolean enabled, boolean failOnError, int maxBackups) throws IOException {
        if (!enabled || dbFile == null || !dbFile.exists() || dbFile.length() == 0) {
            return null;
        }

        if (!backupDir.exists()) {
            backupDir.mkdirs();
        }

        String backupName = String.format("database-before-v%d-%d.db", targetVersion, System.currentTimeMillis());
        File backupFile = new File(backupDir, backupName);

        try {
            Files.copy(dbFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            logger.info("Migration öncesi veritabanı yedeği alındı: " + backupFile.getName());
            cleanOldBackups(backupDir, maxBackups);
            return backupFile;
        } catch (IOException e) {
            logger.severe("Veritabanı yedeği alınırken hata oluştu: " + e.getMessage());
            if (failOnError) {
                throw e;
            }
            return null;
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
        }
    }
}

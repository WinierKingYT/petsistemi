package com.petsistemi.task;

import com.petsistemi.persistence.AdminPersistenceService;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Periodically backs up the SQLite/MySQL database in a WAL-safe async task.
 */
public class AutoBackupTask implements Runnable {

    private final JavaPlugin plugin;
    private final AdminPersistenceService adminPersistenceService;
    private final Logger logger;

    public AutoBackupTask(JavaPlugin plugin, AdminPersistenceService adminPersistenceService) {
        this.plugin = Objects.requireNonNull(plugin, "plugin null olamaz.");
        this.adminPersistenceService = adminPersistenceService;
        this.logger = plugin.getLogger();
    }

    @Override
    public void run() {
        if (adminPersistenceService == null) return;
        try {
            File dbFile = new File(plugin.getDataFolder(), "petsistemi.db");
            File backupDir = new File(plugin.getDataFolder(), "backups");
            if (!backupDir.exists()) backupDir.mkdirs();

            logger.info("[AutoBackupTask] Otomatik veritabanı yedeği başlatılıyor...");
            adminPersistenceService.createBackupAsync(dbFile, backupDir, 10).thenAccept(backupFile -> {
                logger.info("[AutoBackupTask] Otomatik veritabanı yedeği başarıyla alındı: " + backupFile.getName());
            }).exceptionally(ex -> {
                logger.warning("[AutoBackupTask] Otomatik veritabanı yedeği sırasında hata: " + ex.getMessage());
                return null;
            });
        } catch (Exception e) {
            logger.warning("[AutoBackupTask] Yedeğe başlarken hata: " + e.getMessage());
        }
    }
}

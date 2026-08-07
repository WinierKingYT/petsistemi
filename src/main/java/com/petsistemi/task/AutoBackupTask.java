package com.petsistemi.task;

import com.petsistemi.persistence.AdminPersistenceService;
import com.petsistemi.persistence.DatabaseManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Periodically backs up the database in a WAL-safe async task.
 *
 * <p>File and folder names come from {@link DatabaseManager} on purpose. This task used to
 * carry its own literals ({@code petsistemi.db}, {@code backups}) which matched nothing:
 * the backup manager silently returns {@code null} for a missing source, so every run
 * quietly backed up nothing while looking scheduled and healthy.</p>
 */
public class AutoBackupTask implements Runnable {

    private final JavaPlugin plugin;
    private final AdminPersistenceService adminPersistenceService;
    private final int maxBackups;
    private final Logger logger;

    public AutoBackupTask(JavaPlugin plugin, AdminPersistenceService adminPersistenceService, int maxBackups) {
        this.plugin = Objects.requireNonNull(plugin, "plugin null olamaz.");
        this.adminPersistenceService = adminPersistenceService;
        this.maxBackups = Math.max(1, maxBackups);
        this.logger = plugin.getLogger();
    }

    @Override
    public void run() {
        if (adminPersistenceService == null) return;
        try {
            File dbFile = DatabaseManager.databaseFile(plugin);
            File backupDir = DatabaseManager.backupDirectory(plugin);

            if (!dbFile.exists() || dbFile.length() == 0) {
                // Loud, because the alternative is an admin believing backups are running.
                logger.warning("[AutoBackupTask] Veritabanı dosyası bulunamadı, yedek alınamadı: " + dbFile.getPath());
                return;
            }
            if (!backupDir.exists() && !backupDir.mkdirs()) {
                logger.warning("[AutoBackupTask] Yedek klasörü oluşturulamadı: " + backupDir.getPath());
                return;
            }

            logger.info("[AutoBackupTask] Otomatik veritabanı yedeği başlatılıyor...");
            adminPersistenceService.createBackupAsync(dbFile, backupDir, maxBackups)
                    .thenAccept(backupFile -> {
                        if (backupFile == null) {
                            logger.warning("[AutoBackupTask] Yedek oluşturulamadı (kaynak veritabanı okunamadı).");
                        } else {
                            logger.info("[AutoBackupTask] Otomatik veritabanı yedeği başarıyla alındı: " + backupFile.getName());
                        }
                    })
                    .exceptionally(ex -> {
                        logger.warning("[AutoBackupTask] Otomatik veritabanı yedeği sırasında hata: " + ex);
                        return null;
                    });
        } catch (Exception e) {
            logger.warning("[AutoBackupTask] Yedeğe başlarken hata: " + e.getMessage());
        }
    }
}

package com.petsistemi.persistence;

import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The database file and backup folder names are shared by the migration runner,
 * {@code /petadmin backup} and the scheduled auto-backup. A private copy of either literal
 * silently breaks one of them: the backup manager returns {@code null} for a missing
 * source, so a wrong name produces "no backup" rather than an error.
 */
class DatabasePathsTest {

    private static JavaPlugin pluginWithDataFolder(File dataFolder) {
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getDataFolder()).thenReturn(dataFolder);
        return plugin;
    }

    @Test
    void databaseFileResolvesInsideTheDataFolder() {
        File dataFolder = new File("plugins/PetSistemi");

        File db = DatabaseManager.databaseFile(pluginWithDataFolder(dataFolder));

        assertEquals("database.db", db.getName());
        assertEquals(dataFolder, db.getParentFile());
    }

    @Test
    void backupDirectoryResolvesInsideTheDataFolder() {
        File dataFolder = new File("plugins/PetSistemi");

        File backups = DatabaseManager.backupDirectory(pluginWithDataFolder(dataFolder));

        assertEquals("database-backups", backups.getName());
        assertEquals(dataFolder, backups.getParentFile());
    }

    /**
     * Guards the drift that broke auto-backup: no production class may hardcode these
     * names; they must all come from {@link DatabaseManager}.
     */
    @Test
    void noProductionClassHardcodesTheDatabaseOrBackupPath() throws Exception {
        Path sourceRoot = Path.of("src", "main", "java");
        List<String> offenders;
        try (Stream<Path> files = java.nio.file.Files.walk(sourceRoot)) {
            offenders = files
                    .filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> !p.endsWith("DatabaseManager.java"))
                    .filter(p -> {
                        try {
                            String body = java.nio.file.Files.readString(p);
                            return body.contains("\"database.db\"") || body.contains("\"database-backups\"");
                        } catch (Exception e) {
                            return false;
                        }
                    })
                    .map(p -> sourceRoot.relativize(p).toString())
                    .toList();
        }

        assertTrue(offenders.isEmpty(),
                () -> "Bu dosyalar yolu sabit yazmış, DatabaseManager üzerinden almalı: " + offenders);
    }
}

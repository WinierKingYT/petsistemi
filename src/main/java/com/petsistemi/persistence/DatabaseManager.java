package com.petsistemi.persistence;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseManager implements ConnectionProvider {

    /**
     * Canonical database file name. Anything that reads or backs up the database must use
     * this rather than its own literal — a mismatched copy meant the scheduled auto-backup
     * pointed at a file that never existed and silently backed up nothing.
     */
    public static final String DATABASE_FILE_NAME = "database.db";

    /** Canonical backup folder, shared by migration backups, /petadmin backup and auto-backup. */
    public static final String BACKUP_DIR_NAME = "database-backups";

    private final JavaPlugin plugin;
    private final File dbFile;
    private Connection connection;

    public DatabaseManager(JavaPlugin plugin) {
        if (plugin == null) {
            throw new IllegalArgumentException("JavaPlugin parameter cannot be null. Use a test ConnectionProvider implementation for unit tests.");
        }
        this.plugin = plugin;
        this.dbFile = databaseFile(plugin);
    }

    /** Resolves the database file inside a plugin's data folder. */
    public static File databaseFile(JavaPlugin plugin) {
        return new File(plugin.getDataFolder(), DATABASE_FILE_NAME);
    }

    /** Resolves the backup folder inside a plugin's data folder. */
    public static File backupDirectory(JavaPlugin plugin) {
        return new File(plugin.getDataFolder(), BACKUP_DIR_NAME);
    }

    public File getDbFile() {
        return dbFile;
    }

    public synchronized void initialize() throws Exception {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }

        Class.forName("org.sqlite.JDBC");
        connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
        
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("PRAGMA foreign_keys = ON;");
            stmt.execute("PRAGMA journal_mode = WAL;");
            stmt.execute("PRAGMA busy_timeout = 5000;");
            stmt.execute("PRAGMA synchronous = NORMAL;");
        }

        plugin.getLogger().info("SQLite Veritabanı başarıyla bağlandı ve pragmalar uygulandı.");
    }

    @Override
    public synchronized Connection getConnection() {
        try {
            if (connection == null || connection.isClosed()) {
                connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
                try (Statement stmt = connection.createStatement()) {
                    stmt.execute("PRAGMA foreign_keys = ON;");
                    stmt.execute("PRAGMA journal_mode = WAL;");
                    stmt.execute("PRAGMA busy_timeout = 5000;");
                    stmt.execute("PRAGMA synchronous = NORMAL;");
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("SQLite veritabanı bağlantısı kapalı ve yeniden açılamadı!", e);
        }
        return connection;
    }

    @Override
    public synchronized void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            if (plugin != null) {
                plugin.getLogger().warning("SQLite bağlantısı kapatılırken uyarı: " + e.getMessage());
            }
        }
    }
}

package com.petsistemi.persistence;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseManager {

    private final JavaPlugin plugin;
    private final File dbFile;
    private Connection connection;

    public DatabaseManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.dbFile = plugin != null ? new File(plugin.getDataFolder(), "database.db") : null;
    }

    public synchronized void initialize() throws Exception {
        if (plugin != null && !plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }

        Class.forName("org.sqlite.JDBC");
        if (dbFile != null) {
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("PRAGMA foreign_keys = ON;");
            }
            createTables();
            plugin.getLogger().info("SQLite Veritabanı başarıyla bağlandı ve şemalar doğrulandı.");
        }
    }

    public synchronized Connection getConnection() {
        try {
            if ((connection == null || connection.isClosed()) && dbFile != null) {
                connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
                try (Statement stmt = connection.createStatement()) {
                    stmt.execute("PRAGMA foreign_keys = ON;");
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("SQLite veritabanı bağlantısı kapalı ve yeniden açılamadı!", e);
        }
        return connection;
    }

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

    private void createTables() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS schema_migrations (" +
                    "version INTEGER PRIMARY KEY, " +
                    "applied_at INTEGER NOT NULL" +
                    ");");

            stmt.execute("CREATE TABLE IF NOT EXISTS pets (" +
                    "pet_id TEXT PRIMARY KEY, " +
                    "owner_id TEXT NOT NULL, " +
                    "definition_id TEXT NOT NULL, " +
                    "custom_name TEXT, " +
                    "level INTEGER NOT NULL DEFAULT 1, " +
                    "experience INTEGER NOT NULL DEFAULT 0, " +
                    "state TEXT NOT NULL DEFAULT 'AVAILABLE', " +
                    "created_at INTEGER NOT NULL, " +
                    "updated_at INTEGER NOT NULL" +
                    ");");

            stmt.execute("CREATE INDEX IF NOT EXISTS idx_pets_owner ON pets(owner_id);");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_pets_owner_definition ON pets(owner_id, definition_id);");

            stmt.execute("CREATE TABLE IF NOT EXISTS player_active_pets (" +
                    "owner_id TEXT PRIMARY KEY, " +
                    "pet_id TEXT NOT NULL, " +
                    "updated_at INTEGER NOT NULL, " +
                    "FOREIGN KEY (pet_id) REFERENCES pets(pet_id) ON DELETE CASCADE" +
                    ");");
        }
    }
}

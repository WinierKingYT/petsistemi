package com.petsistemi.persistence;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.logging.Level;

public class DatabaseManager {

    private final JavaPlugin plugin;
    private final File dbFile;
    private Connection connection;

    public DatabaseManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.dbFile = new File(plugin.getDataFolder(), "database.db");
    }

    public synchronized void initialize() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }

        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
            createTables();
            plugin.getLogger().info("SQLite Veritabanı başarıyla bağlandı ve şemalar doğrulandı.");
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "SQLite veritabanı bağlantı hatası!", e);
        }
    }

    public synchronized Connection getConnection() {
        try {
            if (connection == null || connection.isClosed()) {
                connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "SQLite bağlantısı yeniden açılırken hata oluştu!", e);
        }
        return connection;
    }

    public synchronized void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "SQLite bağlantısı kapatılırken hata oluştu!", e);
        }
    }

    private void createTables() {
        try (Statement stmt = connection.createStatement()) {
            // Migrations Table
            stmt.execute("CREATE TABLE IF NOT EXISTS schema_migrations (" +
                    "version INTEGER PRIMARY KEY, " +
                    "applied_at INTEGER NOT NULL" +
                    ");");

            // Pets Table
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

            // Indexes
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_pets_owner ON pets(owner_id);");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_pets_owner_definition ON pets(owner_id, definition_id);");

            // Active Pets Table
            stmt.execute("CREATE TABLE IF NOT EXISTS player_active_pets (" +
                    "owner_id TEXT PRIMARY KEY, " +
                    "pet_id TEXT NOT NULL, " +
                    "updated_at INTEGER NOT NULL, " +
                    "FOREIGN KEY (pet_id) REFERENCES pets(pet_id)" +
                    ");");
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Şema tablosu oluşturulurken hata oluştu!", e);
        }
    }
}

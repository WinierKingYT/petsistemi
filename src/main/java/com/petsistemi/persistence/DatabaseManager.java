package com.petsistemi.persistence;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseManager implements ConnectionProvider {

    private final JavaPlugin plugin;
    private final File dbFile;
    private Connection connection;

    public DatabaseManager(JavaPlugin plugin) {
        if (plugin == null) {
            throw new IllegalArgumentException("JavaPlugin parameter cannot be null. Use a test ConnectionProvider implementation for unit tests.");
        }
        this.plugin = plugin;
        this.dbFile = new File(plugin.getDataFolder(), "database.db");
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

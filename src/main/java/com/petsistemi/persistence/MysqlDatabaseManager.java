package com.petsistemi.persistence;

import com.petsistemi.config.PluginConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Properties;

/** Single serialized MySQL connection; all access is already confined to DatabaseExecutor. */
public final class MysqlDatabaseManager implements ConnectionProvider {

    private final JavaPlugin plugin;
    private final PluginConfiguration.MysqlConfiguration config;
    private Connection connection;

    public MysqlDatabaseManager(JavaPlugin plugin, PluginConfiguration.MysqlConfiguration config) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.config = Objects.requireNonNull(config, "config");
    }

    public synchronized void initialize() throws Exception {
        Class.forName("com.mysql.cj.jdbc.Driver");
        connection = open();
        connection.setNetworkTimeout(Runnable::run, Math.max(1000, config.connectTimeoutMs()));
        plugin.getLogger().info("MySQL veritabanına bağlandı: " + config.host() + ":" + config.port()
                + "/" + config.database());
    }

    private Connection open() throws SQLException {
        String url = "jdbc:mysql://" + config.host() + ":" + config.port() + "/" + config.database();
        Properties properties = new Properties();
        properties.setProperty("user", config.username());
        properties.setProperty("password", config.password() != null ? config.password() : "");
        properties.setProperty("useSSL", Boolean.toString(config.useSsl()));
        properties.setProperty("allowPublicKeyRetrieval", "true");
        properties.setProperty("serverTimezone", "UTC");
        properties.setProperty("connectTimeout", Integer.toString(config.connectTimeoutMs()));
        properties.setProperty("socketTimeout", Integer.toString(Math.max(5000, config.connectTimeoutMs() * 3)));
        return DriverManager.getConnection(url, properties);
    }

    @Override
    public synchronized Connection getConnection() {
        try {
            if (connection == null || connection.isClosed() || !connection.isValid(2)) connection = open();
            return connection;
        } catch (SQLException error) {
            throw new PetPersistenceException("MySQL bağlantısı yeniden açılamadı.", error);
        }
    }

    @Override
    public synchronized void close() {
        try {
            if (connection != null && !connection.isClosed()) connection.close();
        } catch (SQLException error) {
            plugin.getLogger().warning("MySQL bağlantısı kapatılırken hata: " + error.getMessage());
        }
    }
}

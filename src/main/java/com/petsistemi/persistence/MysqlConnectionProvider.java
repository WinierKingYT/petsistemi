package com.petsistemi.persistence;

import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class MysqlConnectionProvider implements ConnectionProvider {

    private final JavaPlugin plugin;
    private final String host;
    private final int port;
    private final String database;
    private final String username;
    private final String password;
    private Connection connection;

    public MysqlConnectionProvider(JavaPlugin plugin, String host, int port, String database, String username, String password) {
        this.plugin = plugin;
        this.host = host != null ? host : "localhost";
        this.port = port > 0 ? port : 3306;
        this.database = database != null ? database : "petsistemi";
        this.username = username != null ? username : "root";
        this.password = password != null ? password : "";
    }

    public synchronized void initialize() throws Exception {
        Class.forName("com.mysql.cj.jdbc.Driver");
        String url = String.format("jdbc:mysql://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC", host, port, database);
        this.connection = DriverManager.getConnection(url, username, password);
        if (plugin != null) {
            plugin.getLogger().info("MySQL/MariaDB veritabanı bağlantısı başarıyla kuruldu.");
        }
    }

    @Override
    public synchronized Connection getConnection() {
        int attempts = 0;
        while (attempts < 3) {
            try {
                if (connection == null || connection.isClosed() || !connection.isValid(2)) {
                    String url = String.format("jdbc:mysql://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC", host, port, database);
                    connection = DriverManager.getConnection(url, username, password);
                }
                return connection;
            } catch (SQLException e) {
                attempts++;
                if (attempts >= 3) {
                    throw new RuntimeException("MySQL veritabanı bağlantısı 3 denemeden sonra da kurulamadı!", e);
                }
                try { Thread.sleep(200L * attempts); } catch (InterruptedException ignored) {}
            }
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
                plugin.getLogger().warning("MySQL bağlantısı kapatılırken hata: " + e.getMessage());
            }
        }
    }
}

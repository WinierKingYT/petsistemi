package com.petsistemi.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

/** Creates the current portable schema for MySQL; SQLite historical migrations remain immutable. */
public final class MysqlSchemaMigrator {
    public static final int CURRENT_VERSION = 9;

    private MysqlSchemaMigrator() {}

    public static void migrate(Connection connection) throws SQLException {
        boolean autoCommit = connection.getAutoCommit();
        try {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE TABLE IF NOT EXISTS schema_migrations (version INT PRIMARY KEY, applied_at BIGINT NOT NULL) ENGINE=InnoDB");
                statement.execute("CREATE TABLE IF NOT EXISTS pets (" +
                        "pet_id VARCHAR(36) PRIMARY KEY, owner_id VARCHAR(36) NOT NULL, definition_id VARCHAR(191) NOT NULL, " +
                        "custom_name TEXT NULL, level INT NOT NULL DEFAULT 1, experience BIGINT NOT NULL DEFAULT 0, " +
                        "availability_state VARCHAR(32) NOT NULL DEFAULT 'AVAILABLE', created_at BIGINT NOT NULL, updated_at BIGINT NOT NULL, " +
                        "UNIQUE KEY uq_pets_pet_owner (pet_id, owner_id), KEY idx_pets_owner (owner_id), " +
                        "KEY idx_pets_owner_definition (owner_id, definition_id)) ENGINE=InnoDB");
                statement.execute("CREATE TABLE IF NOT EXISTS player_selected_pets (" +
                        "owner_id VARCHAR(36) PRIMARY KEY, pet_id VARCHAR(36) NOT NULL UNIQUE, selected_at BIGINT NOT NULL, " +
                        "follow_mode VARCHAR(32) NOT NULL DEFAULT 'FOLLOW', " +
                        "CONSTRAINT fk_selected_pet_owner FOREIGN KEY (pet_id, owner_id) REFERENCES pets(pet_id, owner_id) ON DELETE CASCADE) ENGINE=InnoDB");
                statement.execute("CREATE TABLE IF NOT EXISTS pet_audit_log (" +
                        "audit_id BIGINT PRIMARY KEY AUTO_INCREMENT, timestamp BIGINT NOT NULL, actor_type VARCHAR(32) NOT NULL, " +
                        "actor_id VARCHAR(191), action VARCHAR(191) NOT NULL, owner_id VARCHAR(36), pet_id VARCHAR(36), " +
                        "details_json TEXT, success BOOLEAN NOT NULL, KEY idx_audit_timestamp (timestamp)) ENGINE=InnoDB");
                statement.execute("CREATE TABLE IF NOT EXISTS pet_network_events (" +
                        "event_id BIGINT PRIMARY KEY AUTO_INCREMENT, server_id VARCHAR(96) NOT NULL, event_type VARCHAR(64) NOT NULL, " +
                        "owner_id VARCHAR(36), pet_id VARCHAR(36), payload TEXT, created_at BIGINT NOT NULL, " +
                        "KEY idx_network_created (created_at), KEY idx_network_server_event (server_id, event_id)) ENGINE=InnoDB");
                statement.execute("CREATE TABLE IF NOT EXISTS pet_pack_installations (" +
                        "pack_id VARCHAR(191) PRIMARY KEY, namespace VARCHAR(96) NOT NULL, version VARCHAR(64) NOT NULL, " +
                        "sha256 VARCHAR(64) NOT NULL, installed_at BIGINT NOT NULL, source_uri TEXT, manifest_yaml TEXT NOT NULL) ENGINE=InnoDB");
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT IGNORE INTO schema_migrations(version, applied_at) VALUES (?, ?)")) {
                for (int version = 1; version <= CURRENT_VERSION; version++) {
                    statement.setInt(1, version);
                    statement.setLong(2, System.currentTimeMillis());
                    statement.addBatch();
                }
                statement.executeBatch();
            }
            connection.commit();
        } catch (SQLException error) {
            connection.rollback();
            throw error;
        } finally {
            connection.setAutoCommit(autoCommit);
        }
    }
}

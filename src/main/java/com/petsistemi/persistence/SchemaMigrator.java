package com.petsistemi.persistence;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public final class SchemaMigrator {

    private SchemaMigrator() {}

    public static void migrate(Connection connection) throws SQLException {
        boolean autoCommit = connection.getAutoCommit();
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("PRAGMA foreign_keys = ON;");

            // 1. Ensure schema_migrations table exists
            stmt.execute("CREATE TABLE IF NOT EXISTS schema_migrations (" +
                    "version INTEGER PRIMARY KEY, " +
                    "applied_at INTEGER NOT NULL" +
                    ");");

            // 2. Base Schema Creation
            DatabaseSchema.initializeSchema(connection);

            // 3. Migration v1: Ensure player_active_pets pet_id column has UNIQUE constraint
            if (!isMigrationApplied(connection, 1)) {
                applyMigrationV1(connection);
            }
        } finally {
            connection.setAutoCommit(autoCommit);
        }
    }

    private static boolean isMigrationApplied(Connection connection, int version) throws SQLException {
        String sql = "SELECT 1 FROM schema_migrations WHERE version = " + version + ";";
        try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            return rs.next();
        }
    }

    private static void applyMigrationV1(Connection connection) throws SQLException {
        boolean autoCommit = connection.getAutoCommit();
        try {
            connection.setAutoCommit(false);
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("CREATE TABLE IF NOT EXISTS player_active_pets_new (" +
                        "owner_id TEXT PRIMARY KEY, " +
                        "pet_id TEXT NOT NULL UNIQUE, " +
                        "updated_at INTEGER NOT NULL, " +
                        "FOREIGN KEY (pet_id) REFERENCES pets(pet_id) ON DELETE CASCADE" +
                        ");");

                stmt.execute("INSERT OR IGNORE INTO player_active_pets_new (owner_id, pet_id, updated_at) " +
                        "SELECT owner_id, pet_id, MAX(updated_at) FROM player_active_pets GROUP BY owner_id;");

                stmt.execute("DROP TABLE IF EXISTS player_active_pets;");
                stmt.execute("ALTER TABLE player_active_pets_new RENAME TO player_active_pets;");

                stmt.execute("INSERT INTO schema_migrations (version, applied_at) VALUES (1, " + System.currentTimeMillis() + ");");
            }
            connection.commit();
        } catch (SQLException e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(autoCommit);
        }
    }
}

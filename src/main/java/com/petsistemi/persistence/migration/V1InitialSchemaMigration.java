package com.petsistemi.persistence.migration;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Historical migration.
 * Do not modify after release.
 */
public class V1InitialSchemaMigration implements DatabaseMigration {

    @Override
    public int version() {
        return 1;
    }

    @Override
    public String name() {
        return "Initial Base Schema";
    }

    @Override
    public void apply(Connection connection) throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("PRAGMA foreign_keys = ON;");

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

            stmt.execute("CREATE TABLE IF NOT EXISTS player_active_pets (" +
                    "owner_id TEXT PRIMARY KEY, " +
                    "pet_id TEXT NOT NULL, " +
                    "updated_at INTEGER NOT NULL, " +
                    "FOREIGN KEY (pet_id) REFERENCES pets(pet_id) ON DELETE CASCADE" +
                    ");");
        }
    }
}

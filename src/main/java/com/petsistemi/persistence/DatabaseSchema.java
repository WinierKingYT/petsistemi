package com.petsistemi.persistence;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public final class DatabaseSchema {

    private DatabaseSchema() {}

    public static void initializeSchema(Connection connection) throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("PRAGMA foreign_keys = ON;");

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

            // Composite Unique Index for Ownership Integrity
            stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS uq_pets_pet_owner ON pets(pet_id, owner_id);");

            // Indexes
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_pets_owner ON pets(owner_id);");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_pets_owner_definition ON pets(owner_id, definition_id);");

            // Active Pets Table with Composite Foreign Key (pet_id, owner_id)
            stmt.execute("CREATE TABLE IF NOT EXISTS player_active_pets (" +
                    "owner_id TEXT PRIMARY KEY, " +
                    "pet_id TEXT NOT NULL UNIQUE, " +
                    "updated_at INTEGER NOT NULL, " +
                    "FOREIGN KEY (pet_id, owner_id) REFERENCES pets(pet_id, owner_id) ON DELETE CASCADE" +
                    ");");
        }
    }
}

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

            // Indexes
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_pets_owner ON pets(owner_id);");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_pets_owner_definition ON pets(owner_id, definition_id);");

            // Active Pets Table (pet_id is UNIQUE to guarantee 1 active pet per pet_id system-wide)
            stmt.execute("CREATE TABLE IF NOT EXISTS player_active_pets (" +
                    "owner_id TEXT PRIMARY KEY, " +
                    "pet_id TEXT NOT NULL UNIQUE, " +
                    "updated_at INTEGER NOT NULL, " +
                    "FOREIGN KEY (pet_id) REFERENCES pets(pet_id) ON DELETE CASCADE" +
                    ");");
        }
    }
}

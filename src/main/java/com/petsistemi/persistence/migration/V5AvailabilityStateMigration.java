package com.petsistemi.persistence.migration;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class V5AvailabilityStateMigration implements DatabaseMigration {

    @Override
    public int version() {
        return 5;
    }

    @Override
    public String name() {
        return "Availability State Column Normalization";
    }

    @Override
    public void apply(Connection connection) throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            // Check for unknown states
            try (ResultSet rs = stmt.executeQuery("SELECT state FROM pets WHERE state NOT IN ('ACTIVE', 'AVAILABLE', 'DISABLED');")) {
                if (rs.next()) {
                    throw new SQLException("Bilinmeyen pet state tespit edildi: " + rs.getString("state"));
                }
            }

            int sourceCount = 0;
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM pets;")) {
                if (rs.next()) {
                    sourceCount = rs.getInt(1);
                }
            }

            stmt.execute("DROP TABLE IF EXISTS pets_v5;");

            stmt.execute("CREATE TABLE pets_v5 (" +
                    "pet_id TEXT PRIMARY KEY, " +
                    "owner_id TEXT NOT NULL, " +
                    "definition_id TEXT NOT NULL, " +
                    "custom_name TEXT, " +
                    "level INTEGER NOT NULL DEFAULT 1, " +
                    "experience INTEGER NOT NULL DEFAULT 0, " +
                    "availability_state TEXT NOT NULL DEFAULT 'AVAILABLE', " +
                    "created_at INTEGER NOT NULL, " +
                    "updated_at INTEGER NOT NULL" +
                    ");");

            stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS uq_pets_v5_pet_owner ON pets_v5(pet_id, owner_id);");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_pets_v5_owner ON pets_v5(owner_id);");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_pets_v5_owner_definition ON pets_v5(owner_id, definition_id);");

            stmt.execute("INSERT INTO pets_v5 (pet_id, owner_id, definition_id, custom_name, level, experience, availability_state, created_at, updated_at) " +
                    "SELECT pet_id, owner_id, definition_id, custom_name, level, experience, " +
                    "CASE WHEN state = 'DISABLED' THEN 'DISABLED' ELSE 'AVAILABLE' END, " +
                    "created_at, updated_at FROM pets;");

            int destCount = 0;
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM pets_v5;")) {
                if (rs.next()) {
                    destCount = rs.getInt(1);
                }
            }

            if (sourceCount != destCount) {
                throw new SQLException("Migration V5 satır sayısı uyuşmazlığı! Beklenen kaynak: " + sourceCount + ", aktarılan hedef: " + destCount);
            }

            stmt.execute("DROP TABLE IF EXISTS pets;");
            stmt.execute("ALTER TABLE pets_v5 RENAME TO pets;");
        }
    }
}

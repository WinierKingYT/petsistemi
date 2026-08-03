package com.petsistemi.persistence.migration;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Historical migration.
 * Do not modify after release.
 */
public class V2UniqueSelectedPetMigration implements DatabaseMigration {

    @Override
    public int version() {
        return 2;
    }

    @Override
    public String name() {
        return "Initial pet_id UNIQUE constraint";
    }

    @Override
    public void apply(Connection connection) throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            boolean hasTable = false;
            try (ResultSet rs = stmt.executeQuery("SELECT 1 FROM sqlite_master WHERE type='table' AND name='player_active_pets';")) {
                if (rs.next()) {
                    hasTable = true;
                }
            }

            if (!hasTable) {
                stmt.execute("CREATE TABLE player_active_pets (" +
                        "owner_id TEXT PRIMARY KEY, " +
                        "pet_id TEXT NOT NULL UNIQUE, " +
                        "updated_at INTEGER NOT NULL, " +
                        "FOREIGN KEY (pet_id) REFERENCES pets(pet_id) ON DELETE CASCADE" +
                        ");");
                return;
            }

            stmt.execute("CREATE TABLE IF NOT EXISTS player_active_pets_v2 (" +
                    "owner_id TEXT PRIMARY KEY, " +
                    "pet_id TEXT NOT NULL UNIQUE, " +
                    "updated_at INTEGER NOT NULL, " +
                    "FOREIGN KEY (pet_id) REFERENCES pets(pet_id) ON DELETE CASCADE" +
                    ");");

            stmt.execute("INSERT OR IGNORE INTO player_active_pets_v2 (owner_id, pet_id, updated_at) " +
                    "SELECT pap.owner_id, pap.pet_id, pap.updated_at FROM player_active_pets pap " +
                    "INNER JOIN pets p ON pap.pet_id = p.pet_id AND pap.owner_id = p.owner_id;");

            stmt.execute("DROP TABLE IF EXISTS player_active_pets;");
            stmt.execute("ALTER TABLE player_active_pets_v2 RENAME TO player_active_pets;");
        }
    }
}

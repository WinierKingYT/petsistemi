package com.petsistemi.persistence.migration;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Historical migration.
 * Do not modify after release.
 */
public class V3CompositeForeignKeyMigration implements DatabaseMigration {

    @Override
    public int version() {
        return 3;
    }

    @Override
    public String name() {
        return "Composite Foreign Key Constraint";
    }

    @Override
    public void apply(Connection connection) throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS uq_pets_pet_owner ON pets(pet_id, owner_id);");

            stmt.execute("DROP TABLE IF EXISTS player_active_pets_v3;");

            stmt.execute("CREATE TABLE player_active_pets_v3 (" +
                    "owner_id TEXT PRIMARY KEY, " +
                    "pet_id TEXT NOT NULL UNIQUE, " +
                    "updated_at INTEGER NOT NULL, " +
                    "FOREIGN KEY (pet_id, owner_id) REFERENCES pets(pet_id, owner_id) ON DELETE CASCADE" +
                    ");");

            int sourceCount = 0;
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM player_active_pets;")) {
                if (rs.next()) {
                    sourceCount = rs.getInt(1);
                }
            }

            stmt.execute("INSERT INTO player_active_pets_v3 (owner_id, pet_id, updated_at) " +
                    "SELECT a.owner_id, a.pet_id, a.updated_at FROM player_active_pets a " +
                    "JOIN pets p ON a.pet_id = p.pet_id AND a.owner_id = p.owner_id;");

            int destCount = 0;
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM player_active_pets_v3;")) {
                if (rs.next()) {
                    destCount = rs.getInt(1);
                }
            }

            if (sourceCount != destCount) {
                throw new SQLException("Migration V3 satır sayısı uyuşmazlığı! Beklenen kaynak: " + sourceCount + ", aktarılan hedef: " + destCount);
            }

            stmt.execute("DROP TABLE IF EXISTS player_active_pets;");
            stmt.execute("ALTER TABLE player_active_pets_v3 RENAME TO player_active_pets;");
        }
    }
}

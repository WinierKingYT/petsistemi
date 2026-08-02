package com.petsistemi.persistence.migration;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class V6SelectionTableRenameMigration implements DatabaseMigration {

    @Override
    public int version() {
        return 6;
    }

    @Override
    public String name() {
        return "Rename player_active_pets to player_selected_pets";
    }

    @Override
    public void apply(Connection connection) throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            boolean hasOldTable = false;
            try (ResultSet rs = stmt.executeQuery("SELECT 1 FROM sqlite_master WHERE type='table' AND name='player_active_pets';")) {
                if (rs.next()) {
                    hasOldTable = true;
                }
            }

            stmt.execute("CREATE TABLE IF NOT EXISTS player_selected_pets (" +
                    "owner_id TEXT PRIMARY KEY, " +
                    "pet_id TEXT NOT NULL UNIQUE, " +
                    "selected_at INTEGER NOT NULL, " +
                    "FOREIGN KEY (pet_id, owner_id) REFERENCES pets(pet_id, owner_id) ON DELETE CASCADE" +
                    ");");

            if (hasOldTable) {
                stmt.execute("INSERT OR IGNORE INTO player_selected_pets (owner_id, pet_id, selected_at) " +
                        "SELECT owner_id, pet_id, updated_at FROM player_active_pets;");
                stmt.execute("DROP TABLE IF EXISTS player_active_pets;");
            }
        }
    }
}

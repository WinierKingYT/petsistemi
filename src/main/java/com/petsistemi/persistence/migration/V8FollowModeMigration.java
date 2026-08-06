package com.petsistemi.persistence.migration;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Adds the per-selection follow mode column so the pet's runtime mode
 * (follow/stay/wander) survives restarts.
 */
public class V8FollowModeMigration implements DatabaseMigration {

    @Override
    public int version() {
        return 8;
    }

    @Override
    public String name() {
        return "Add follow_mode to player_selected_pets";
    }

    @Override
    public void apply(Connection connection) throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            boolean hasColumn = false;
            try (ResultSet rs = stmt.executeQuery("PRAGMA table_info(player_selected_pets);")) {
                while (rs.next()) {
                    if ("follow_mode".equalsIgnoreCase(rs.getString("name"))) {
                        hasColumn = true;
                        break;
                    }
                }
            }
            if (!hasColumn) {
                stmt.execute("ALTER TABLE player_selected_pets ADD COLUMN follow_mode TEXT NOT NULL DEFAULT 'FOLLOW';");
            }
        }
    }
}

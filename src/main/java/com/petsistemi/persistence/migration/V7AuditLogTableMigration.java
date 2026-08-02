package com.petsistemi.persistence.migration;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class V7AuditLogTableMigration implements DatabaseMigration {

    @Override
    public int version() {
        return 7;
    }

    @Override
    public String name() {
        return "Create pet_audit_log table";
    }

    @Override
    public void apply(Connection connection) throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS pet_audit_log (" +
                    "audit_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "timestamp INTEGER NOT NULL, " +
                    "actor_type TEXT NOT NULL, " +
                    "actor_id TEXT, " +
                    "action TEXT NOT NULL, " +
                    "owner_id TEXT, " +
                    "pet_id TEXT, " +
                    "details_json TEXT, " +
                    "success INTEGER NOT NULL" +
                    ");");
        }
    }
}

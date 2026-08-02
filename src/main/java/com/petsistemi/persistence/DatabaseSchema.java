package com.petsistemi.persistence;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public final class DatabaseSchema {

    private DatabaseSchema() {}

    public static void initializeSchema(Connection connection) throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("PRAGMA foreign_keys = ON;");
        }
        SchemaMigrator.migrate(connection);
    }
}

package com.petsistemi.persistence.migration;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/** SQLite ecosystem tables. MySQL creates the equivalent schema in MysqlSchemaMigrator. */
public final class V9EcosystemTablesMigration implements DatabaseMigration {
    @Override public int version() { return 9; }
    @Override public String name() { return "MF8 network events and pet pack registry"; }

    @Override public void apply(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS pet_network_events (" +
                    "event_id INTEGER PRIMARY KEY AUTOINCREMENT, server_id TEXT NOT NULL, event_type TEXT NOT NULL, " +
                    "owner_id TEXT, pet_id TEXT, payload TEXT, created_at INTEGER NOT NULL)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_network_events_created ON pet_network_events(created_at)");
            statement.execute("CREATE TABLE IF NOT EXISTS pet_pack_installations (" +
                    "pack_id TEXT PRIMARY KEY, namespace TEXT NOT NULL, version TEXT NOT NULL, sha256 TEXT NOT NULL, " +
                    "installed_at INTEGER NOT NULL, source_uri TEXT, manifest_yaml TEXT NOT NULL)");
        }
    }
}

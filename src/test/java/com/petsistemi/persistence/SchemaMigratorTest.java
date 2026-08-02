package com.petsistemi.persistence;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SchemaMigratorTest {

    private Connection connection;

    @BeforeEach
    void setUp() throws Exception {
        connection = DriverManager.getConnection("jdbc:sqlite::memory:");
        
        // Create old pre-v1 schema without UNIQUE constraint on pet_id
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("PRAGMA foreign_keys = ON;");
            stmt.execute("CREATE TABLE pets (" +
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

            stmt.execute("CREATE TABLE player_active_pets (" +
                    "owner_id TEXT PRIMARY KEY, " +
                    "pet_id TEXT NOT NULL, " +
                    "updated_at INTEGER NOT NULL, " +
                    "FOREIGN KEY (pet_id) REFERENCES pets(pet_id) ON DELETE CASCADE" +
                    ");");
        }
    }

    @AfterEach
    void tearDown() throws Exception {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }

    @Test
    void testMigrationV1WithDuplicatePetIdsConvertsSupersededToAvailable() throws Exception {
        UUID sharedPetId = UUID.randomUUID();
        UUID ownerA = UUID.randomUUID();
        UUID ownerB = UUID.randomUUID();

        try (Statement stmt = connection.createStatement()) {
            stmt.execute("INSERT INTO pets VALUES ('" + sharedPetId + "', '" + ownerA + "', 'wolf', 'Dog', 1, 0, 'ACTIVE', 100, 100);");
            stmt.execute("INSERT INTO player_active_pets VALUES ('" + ownerA + "', '" + sharedPetId + "', 100);");
            stmt.execute("INSERT INTO player_active_pets VALUES ('" + ownerB + "', '" + sharedPetId + "', 500);"); // Newer selection by Owner B
        }

        // Run Migration
        SchemaMigrator.migrate(connection);

        // Verify version 1 recorded
        try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery("SELECT version FROM schema_migrations;")) {
            assertTrue(rs.next());
            assertEquals(1, rs.getInt("version"));
        }

        // Verify UNIQUE constraint enforced by attempting duplicate insertion
        UUID ownerC = UUID.randomUUID();
        try (Statement stmt = connection.createStatement()) {
            assertThrows(Exception.class, () -> 
                stmt.execute("INSERT INTO player_active_pets VALUES ('" + ownerC + "', '" + sharedPetId + "', 600);")
            );
        }

        // Verify Owner B (most recent updated_at) retained selection
        try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery("SELECT owner_id FROM player_active_pets WHERE pet_id = '" + sharedPetId + "';")) {
            assertTrue(rs.next());
            assertEquals(ownerB.toString(), rs.getString("owner_id"));
            assertFalse(rs.next()); // Only 1 unique row exists
        }

        // Verify superseded pet state is set to AVAILABLE
        try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery("SELECT state FROM pets WHERE pet_id = '" + sharedPetId + "';")) {
            assertTrue(rs.next());
            assertEquals("AVAILABLE", rs.getString("state"));
        }
    }

    @Test
    void testMigrationIdempotency() throws Exception {
        SchemaMigrator.migrate(connection);
        assertDoesNotThrow(() -> SchemaMigrator.migrate(connection));
    }
}

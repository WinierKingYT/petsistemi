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
        
        // Create old pre-v1 schema without UNIQUE constraint or composite FK
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
    void testMigrationV2ProtectsActualOwnerAndDeletesImposterSelections() throws Exception {
        UUID petId = UUID.randomUUID();
        UUID actualOwnerA = UUID.randomUUID();
        UUID imposterOwnerB = UUID.randomUUID();

        try (Statement stmt = connection.createStatement()) {
            stmt.execute("INSERT INTO pets VALUES ('" + petId + "', '" + actualOwnerA + "', 'wolf', 'Dog', 1, 0, 'AVAILABLE', 100, 100);");
            stmt.execute("INSERT INTO player_active_pets VALUES ('" + actualOwnerA + "', '" + petId + "', 100);");
            stmt.execute("INSERT INTO player_active_pets VALUES ('" + imposterOwnerB + "', '" + petId + "', 500);"); // Imposter with higher timestamp
        }

        // Run Migration
        SchemaMigrator.migrate(connection);

        // Verify version 1, 2, and 3 recorded
        try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM schema_migrations;")) {
            assertTrue(rs.next());
            assertEquals(3, rs.getInt(1));
        }

        // Verify Actual Owner A retained selection despite lower timestamp
        try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery("SELECT owner_id FROM player_active_pets WHERE pet_id = '" + petId + "';")) {
            assertTrue(rs.next());
            assertEquals(actualOwnerA.toString(), rs.getString("owner_id"));
            assertFalse(rs.next()); // Only 1 unique valid row exists
        }

        // Verify pet state is set to ACTIVE for actual owner
        try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery("SELECT state FROM pets WHERE pet_id = '" + petId + "';")) {
            assertTrue(rs.next());
            assertEquals("ACTIVE", rs.getString("state"));
        }

        // Verify Composite Foreign Key (pet_id, owner_id) prevents imposter assignment
        try (Statement stmt = connection.createStatement()) {
            assertThrows(Exception.class, () -> 
                stmt.execute("INSERT INTO player_active_pets VALUES ('" + imposterOwnerB + "', '" + petId + "', 600);")
            );
        }
    }

    @Test
    void testMigrationV2DeletesAllSelectionsIfNoActualOwnerSelectionExists() throws Exception {
        UUID petId = UUID.randomUUID();
        UUID actualOwnerA = UUID.randomUUID();
        UUID imposterOwnerB = UUID.randomUUID();

        try (Statement stmt = connection.createStatement()) {
            stmt.execute("INSERT INTO pets VALUES ('" + petId + "', '" + actualOwnerA + "', 'wolf', 'Dog', 1, 0, 'ACTIVE', 100, 100);");
            stmt.execute("INSERT INTO player_active_pets VALUES ('" + imposterOwnerB + "', '" + petId + "', 500);"); // Only imposter selection exists
        }

        SchemaMigrator.migrate(connection);

        // Verify imposter selection deleted
        try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM player_active_pets WHERE pet_id = '" + petId + "';")) {
            assertTrue(rs.next());
            assertEquals(0, rs.getInt(1));
        }

        // Verify pet state converted to AVAILABLE
        try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery("SELECT state FROM pets WHERE pet_id = '" + petId + "';")) {
            assertTrue(rs.next());
            assertEquals("AVAILABLE", rs.getString("state"));
        }
    }

    @Test
    void testDisabledPetSelectionDeletedAndStateStaysDisabled() throws Exception {
        UUID petId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();

        try (Statement stmt = connection.createStatement()) {
            stmt.execute("INSERT INTO pets VALUES ('" + petId + "', '" + ownerId + "', 'wolf', 'DisabledDog', 1, 0, 'DISABLED', 100, 100);");
            stmt.execute("INSERT INTO player_active_pets VALUES ('" + ownerId + "', '" + petId + "', 100);");
        }

        SchemaMigrator.migrate(connection);

        // Verify selection deleted
        try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM player_active_pets WHERE pet_id = '" + petId + "';")) {
            assertTrue(rs.next());
            assertEquals(0, rs.getInt(1));
        }

        // Verify pet state stays DISABLED
        try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery("SELECT state FROM pets WHERE pet_id = '" + petId + "';")) {
            assertTrue(rs.next());
            assertEquals("DISABLED", rs.getString("state"));
        }
    }

    @Test
    void testUnselectedActivePetReconciledToAvailable() throws Exception {
        UUID petId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();

        try (Statement stmt = connection.createStatement()) {
            // Unselected pet marked ACTIVE by legacy bug or manual DB edit
            stmt.execute("INSERT INTO pets VALUES ('" + petId + "', '" + ownerId + "', 'wolf', 'OrphanDog', 1, 0, 'ACTIVE', 100, 100);");
        }

        SchemaMigrator.migrate(connection);

        // Verify pet state reconciled to AVAILABLE
        try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery("SELECT state FROM pets WHERE pet_id = '" + petId + "';")) {
            assertTrue(rs.next());
            assertEquals("AVAILABLE", rs.getString("state"));
        }
    }

    @Test
    void testMigrationIdempotencyAndDataIntegrity() throws Exception {
        UUID petId = UUID.randomUUID();
        UUID actualOwnerA = UUID.randomUUID();
        long timestamp = 123456789L;

        try (Statement stmt = connection.createStatement()) {
            stmt.execute("INSERT INTO pets VALUES ('" + petId + "', '" + actualOwnerA + "', 'wolf', 'Dog', 1, 0, 'AVAILABLE', " + timestamp + ", " + timestamp + ");");
            stmt.execute("INSERT INTO player_active_pets VALUES ('" + actualOwnerA + "', '" + petId + "', " + timestamp + ");");
        }

        SchemaMigrator.migrate(connection);
        
        // Record data snapshot after 1st run
        String activeOwnerAfter1st = null;
        long timestampAfter1st = 0;
        String petStateAfter1st = null;

        try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery("SELECT owner_id, updated_at FROM player_active_pets WHERE pet_id = '" + petId + "';")) {
            assertTrue(rs.next());
            activeOwnerAfter1st = rs.getString("owner_id");
            timestampAfter1st = rs.getLong("updated_at");
        }

        try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery("SELECT state FROM pets WHERE pet_id = '" + petId + "';")) {
            assertTrue(rs.next());
            petStateAfter1st = rs.getString("state");
        }

        // Run Migration 2nd time
        assertDoesNotThrow(() -> SchemaMigrator.migrate(connection));

        // Verify 2nd run produced identical data
        try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery("SELECT owner_id, updated_at FROM player_active_pets WHERE pet_id = '" + petId + "';")) {
            assertTrue(rs.next());
            assertEquals(activeOwnerAfter1st, rs.getString("owner_id"));
            assertEquals(timestampAfter1st, rs.getLong("updated_at"));
        }

        try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery("SELECT state FROM pets WHERE pet_id = '" + petId + "';")) {
            assertTrue(rs.next());
            assertEquals(petStateAfter1st, rs.getString("state"));
        }
    }
}

package com.petsistemi.persistence;

import com.petsistemi.persistence.migration.DatabaseMigration;
import com.petsistemi.persistence.migration.MigrationBackupManager;
import com.petsistemi.persistence.migration.MigrationRunner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class SchemaMigratorTest {

    private Connection connection;
    private Logger logger;

    @TempDir
    File tempDir;

    @BeforeEach
    void setUp() throws Exception {
        connection = DriverManager.getConnection("jdbc:sqlite::memory:");
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("PRAGMA foreign_keys = ON;");
        }
        logger = Logger.getLogger("TestLogger");
    }

    @AfterEach
    void tearDown() throws Exception {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }

    private void executeSqlResource(String path) throws Exception {
        try (InputStream is = getClass().getResourceAsStream(path)) {
            assertNotNull(is, "SQL fixture resource not found: " + path);
            String sql = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            try (Statement stmt = connection.createStatement()) {
                for (String cmd : sql.split(";")) {
                    if (!cmd.trim().isEmpty()) {
                        stmt.execute(cmd.trim());
                    }
                }
            }
        }
    }

    @Test
    void migratesFreshDatabaseToLatest() throws Exception {
        SchemaMigrator.migrate(connection);

        try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM schema_migrations;")) {
            assertTrue(rs.next());
            assertEquals(7, rs.getInt(1));
        }
    }

    @Test
    void upgradesPreviouslyAppliedV3ToV4V5V6() throws Exception {
        executeSqlResource("/migrations/v3-schema.sql");
        executeSqlResource("/migrations/v3-disabled-selection.sql");
        executeSqlResource("/migrations/v3-orphan-active.sql");

        SchemaMigrator.migrate(connection);

        try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM schema_migrations;")) {
            assertTrue(rs.next());
            assertEquals(7, rs.getInt(1));
        }
    }

    @Test
    void keepsDisabledPetDisabled() throws Exception {
        executeSqlResource("/migrations/v3-schema.sql");
        executeSqlResource("/migrations/v3-disabled-selection.sql");

        SchemaMigrator.migrate(connection);

        try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery("SELECT availability_state FROM pets WHERE pet_id = 'pet-disabled-1';")) {
            assertTrue(rs.next());
            assertEquals("DISABLED", rs.getString("availability_state"));
        }
    }

    @Test
    void removesDisabledPetSelection() throws Exception {
        executeSqlResource("/migrations/v3-schema.sql");
        executeSqlResource("/migrations/v3-disabled-selection.sql");

        SchemaMigrator.migrate(connection);

        try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM player_selected_pets WHERE pet_id = 'pet-disabled-1';")) {
            assertTrue(rs.next());
            assertEquals(0, rs.getInt(1));
        }
    }

    @Test
    void reconcilesOrphanActivePet() throws Exception {
        executeSqlResource("/migrations/v3-schema.sql");
        executeSqlResource("/migrations/v3-orphan-active.sql");

        SchemaMigrator.migrate(connection);

        try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery("SELECT availability_state FROM pets WHERE pet_id = 'pet-orphan-1';")) {
            assertTrue(rs.next());
            assertEquals("AVAILABLE", rs.getString("availability_state"));
        }
    }

    @Test
    void removesImposterSelection() throws Exception {
        executeSqlResource("/migrations/v1-schema.sql");
        executeSqlResource("/migrations/v3-imposter-selection.sql");

        SchemaMigrator.migrate(connection);

        try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM player_selected_pets WHERE owner_id = 'owner-imposter';")) {
            assertTrue(rs.next());
            assertEquals(0, rs.getInt(1));
        }
    }

    @Test
    void keepsValidOwnerSelection() throws Exception {
        executeSqlResource("/migrations/v1-schema.sql");
        executeSqlResource("/migrations/v3-imposter-selection.sql");

        SchemaMigrator.migrate(connection);

        try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery("SELECT owner_id FROM player_selected_pets WHERE pet_id = 'pet-imposter-1';")) {
            assertTrue(rs.next());
            assertEquals("owner-actual", rs.getString("owner_id"));
            assertFalse(rs.next());
        }
    }

    @Test
    void cleansAbortedTemporaryTable() throws Exception {
        executeSqlResource("/migrations/v2-schema.sql");
        executeSqlResource("/migrations/v3-aborted-temp-table.sql");

        assertDoesNotThrow(() -> SchemaMigrator.migrate(connection));
    }

    @Test
    void doesNotDuplicateMigrationRows() throws Exception {
        SchemaMigrator.migrate(connection);
        SchemaMigrator.migrate(connection);

        try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM schema_migrations;")) {
            assertTrue(rs.next());
            assertEquals(7, rs.getInt(1));
        }
    }

    @Test
    void migrationIsIdempotent() throws Exception {
        UUID petId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();

        try (Statement stmt = connection.createStatement()) {
            stmt.execute("CREATE TABLE schema_migrations (version INTEGER PRIMARY KEY, applied_at INTEGER NOT NULL);");
            stmt.execute("CREATE TABLE pets (pet_id TEXT PRIMARY KEY, owner_id TEXT, definition_id TEXT, custom_name TEXT, level INTEGER, experience INTEGER, state TEXT, created_at INTEGER, updated_at INTEGER);");
            stmt.execute("CREATE TABLE player_active_pets (owner_id TEXT PRIMARY KEY, pet_id TEXT, updated_at INTEGER);");
            stmt.execute("INSERT INTO pets VALUES ('" + petId + "', '" + ownerId + "', 'wolf', 'Bobi', 1, 0, 'AVAILABLE', 100, 100);");
            stmt.execute("INSERT INTO player_active_pets VALUES ('" + ownerId + "', '" + petId + "', 100);");
        }

        SchemaMigrator.migrate(connection);
        assertDoesNotThrow(() -> SchemaMigrator.migrate(connection));

        try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery("SELECT owner_id FROM player_selected_pets WHERE pet_id = '" + petId + "';")) {
            assertTrue(rs.next());
            assertEquals(ownerId.toString(), rs.getString("owner_id"));
        }
    }

    @Test
    void rollsBackFailedMigration() throws Exception {
        DatabaseMigration failingMigration = new DatabaseMigration() {
            @Override public int version() { return 99; }
            @Override public String name() { return "Failing Migration Test"; }
            @Override public void apply(Connection conn) throws SQLException {
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("CREATE TABLE test_rollback (id INT);");
                    throw new SQLException("Simulated Migration Failure");
                }
            }
        };

        List<DatabaseMigration> migrations = List.of(new com.petsistemi.persistence.migration.V1InitialSchemaMigration(), failingMigration);
        MigrationRunner runner = new MigrationRunner(logger, migrations, new MigrationBackupManager(logger));

        assertThrows(SQLException.class, () -> runner.run(connection, null, null, false, false, 5));

        // Verify test_rollback table was rolled back
        try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery("SELECT 1 FROM sqlite_master WHERE type='table' AND name='test_rollback';")) {
            assertFalse(rs.next(), "Table created in failing migration must be rolled back");
        }
    }

    @Test
    void testV5PreservesActivePetSelectionWithForeignKeysOn() throws Exception {
        executeSqlResource("/migrations/v3-schema.sql");
        
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("INSERT INTO pets (pet_id, owner_id, definition_id, custom_name, level, experience, state, created_at, updated_at) " +
                    "VALUES ('pet-123', 'owner-456', 'wolf', 'Wolfy', 1, 0, 'ACTIVE', 100, 100);");
            stmt.execute("INSERT INTO player_active_pets (owner_id, pet_id, updated_at) " +
                    "VALUES ('owner-456', 'pet-123', 100);");
        }

        SchemaMigrator.migrate(connection);

        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT owner_id, pet_id FROM player_selected_pets WHERE owner_id = 'owner-456';")) {
            assertTrue(rs.next(), "Selection must be preserved after V5/V6 migrations even with foreign_keys = ON");
            assertEquals("pet-123", rs.getString("pet_id"));
        }
    }

    @Test
    void testLegacyV2ImposterRepair() throws Exception {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("CREATE TABLE schema_migrations (version INTEGER PRIMARY KEY, applied_at INTEGER NOT NULL);");
            stmt.execute("INSERT INTO schema_migrations VALUES (1, 100), (2, 200);");
            stmt.execute("CREATE TABLE pets (pet_id TEXT PRIMARY KEY, owner_id TEXT, definition_id TEXT, custom_name TEXT, level INTEGER, experience INTEGER, state TEXT, created_at INTEGER, updated_at INTEGER);");
            stmt.execute("CREATE TABLE player_active_pets (owner_id TEXT PRIMARY KEY, pet_id TEXT, updated_at INTEGER);");

            // Real pet and valid selection
            stmt.execute("INSERT INTO pets VALUES ('pet-real', 'owner-real', 'wolf', 'Bobi', 1, 0, 'AVAILABLE', 100, 100);");
            stmt.execute("INSERT INTO player_active_pets VALUES ('owner-real', 'pet-real', 100);");

            // Imposter selection inserted under broken 954e114 V2 migration
            stmt.execute("INSERT INTO player_active_pets VALUES ('owner-imposter', 'pet-nonexistent', 100);");
        }

        assertDoesNotThrow(() -> SchemaMigrator.migrate(connection));

        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM schema_migrations;")) {
            assertTrue(rs.next());
            assertEquals(7, rs.getInt(1));
        }

        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT owner_id FROM player_selected_pets WHERE pet_id = 'pet-real';")) {
            assertTrue(rs.next());
            assertEquals("owner-real", rs.getString("owner_id"));
        }
    }
}

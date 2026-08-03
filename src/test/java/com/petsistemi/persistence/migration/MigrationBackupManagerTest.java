package com.petsistemi.persistence.migration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class MigrationBackupManagerTest {

    private Connection connection;
    private Logger logger;
    private File dbFile;

    @TempDir
    File tempDir;

    @BeforeEach
    void setUp() throws Exception {
        logger = Logger.getLogger("BackupTestLogger");
        dbFile = new File(tempDir, "test-database.db");
        connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());

        try (Statement stmt = connection.createStatement()) {
            stmt.execute("PRAGMA journal_mode = WAL;");
            stmt.execute("CREATE TABLE test_data (id INT PRIMARY KEY, name TEXT);");
            stmt.execute("INSERT INTO test_data VALUES (1, 'Sample');");
        }
    }

    @AfterEach
    void tearDown() throws Exception {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }

    @Test
    void testWalSafeBackupAndIntegrityCheck() throws Exception {
        MigrationBackupManager manager = new MigrationBackupManager(logger);
        File backupDir = new File(tempDir, "backups");

        File backupFile = manager.createBackup(connection, dbFile, backupDir, 1, true, true, 5);

        assertNotNull(backupFile);
        assertTrue(backupFile.exists());
        assertTrue(backupFile.length() > 0);

        // Verify backup contents with fresh connection
        try (Connection backupConn = DriverManager.getConnection("jdbc:sqlite:" + backupFile.getAbsolutePath());
             Statement stmt = backupConn.createStatement()) {
            var rs = stmt.executeQuery("SELECT name FROM test_data WHERE id = 1;");
            assertTrue(rs.next());
            assertEquals("Sample", rs.getString("name"));
        }
    }
}

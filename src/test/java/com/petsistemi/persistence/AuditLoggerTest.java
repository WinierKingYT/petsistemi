package com.petsistemi.persistence;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class AuditLoggerTest {

    private Connection connection;
    private AuditLogger auditLogger;

    @BeforeEach
    void setUp() throws Exception {
        connection = DriverManager.getConnection("jdbc:sqlite::memory:");
        SchemaMigrator.migrate(connection);

        ConnectionProvider provider = new ConnectionProvider() {
            @Override public Connection getConnection() { return connection; }
            @Override public void close() {}
        };

        auditLogger = new AuditLogger(provider, Logger.getLogger("TestLogger"));
    }

    @AfterEach
    void tearDown() throws Exception {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }

    @Test
    void testAuditLogInsertion() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID petId = UUID.randomUUID();

        auditLogger.log("ADMIN", "Console", "GIVE_PET", ownerId, petId, "{\"def\":\"wolf\"}", true);

        try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery("SELECT * FROM pet_audit_log;")) {
            assertTrue(rs.next());
            assertEquals("ADMIN", rs.getString("actor_type"));
            assertEquals("GIVE_PET", rs.getString("action"));
            assertEquals(ownerId.toString(), rs.getString("owner_id"));
            assertEquals(1, rs.getInt("success"));
        }
    }
}

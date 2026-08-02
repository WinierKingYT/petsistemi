package com.petsistemi.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public final class SchemaMigrator {

    private static final Logger LOGGER = Logger.getLogger(SchemaMigrator.class.getName());

    private SchemaMigrator() {}

    public static void migrate(Connection connection) throws SQLException {
        boolean autoCommit = connection.getAutoCommit();
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("PRAGMA foreign_keys = ON;");

            stmt.execute("CREATE TABLE IF NOT EXISTS schema_migrations (" +
                    "version INTEGER PRIMARY KEY, " +
                    "applied_at INTEGER NOT NULL" +
                    ");");

            DatabaseSchema.initializeSchema(connection);

            if (!isMigrationApplied(connection, 1)) {
                applyMigrationV1(connection);
            }
        } finally {
            connection.setAutoCommit(autoCommit);
        }
    }

    private static boolean isMigrationApplied(Connection connection, int version) throws SQLException {
        String sql = "SELECT 1 FROM schema_migrations WHERE version = " + version + ";";
        try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            return rs.next();
        }
    }

    private static void applyMigrationV1(Connection connection) throws SQLException {
        boolean autoCommit = connection.getAutoCommit();
        try {
            connection.setAutoCommit(false);
            
            // 1. Count total selection records
            int totalInspected = 0;
            try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM player_active_pets;")) {
                if (rs.next()) {
                    totalInspected = rs.getInt(1);
                }
            }

            // 2. Detect duplicate pet_id selections across multiple owners
            List<String> duplicatePetIds = new ArrayList<>();
            String checkDuplicatesSql = "SELECT pet_id FROM player_active_pets GROUP BY pet_id HAVING COUNT(*) > 1;";
            try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(checkDuplicatesSql)) {
                while (rs.next()) {
                    duplicatePetIds.add(rs.getString("pet_id"));
                }
            }

            int supersededCount = 0;
            if (!duplicatePetIds.isEmpty()) {
                for (String petId : duplicatePetIds) {
                    supersededCount += resolveDuplicatePetId(connection, petId);
                }
            }

            // 3. Create player_active_pets_new table with UNIQUE constraint
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("CREATE TABLE IF NOT EXISTS player_active_pets_new (" +
                        "owner_id TEXT PRIMARY KEY, " +
                        "pet_id TEXT NOT NULL UNIQUE, " +
                        "updated_at INTEGER NOT NULL, " +
                        "FOREIGN KEY (pet_id) REFERENCES pets(pet_id) ON DELETE CASCADE" +
                        ");");

                stmt.execute("INSERT INTO player_active_pets_new (owner_id, pet_id, updated_at) " +
                        "SELECT owner_id, pet_id, updated_at FROM player_active_pets;");

                stmt.execute("DROP TABLE IF EXISTS player_active_pets;");
                stmt.execute("ALTER TABLE player_active_pets_new RENAME TO player_active_pets;");

                stmt.execute("INSERT INTO schema_migrations (version, applied_at) VALUES (1, " + System.currentTimeMillis() + ");");
            }

            connection.commit();

            int retainedCount = totalInspected - supersededCount;
            LOGGER.info("[PetSistemi] Migration V1 Raporu:");
            LOGGER.info("  - " + totalInspected + " seçim kaydı incelendi");
            LOGGER.info("  - " + duplicatePetIds.size() + " mükerrer pet seçimi bulundu");
            LOGGER.info("  - " + retainedCount + " güncel seçim kaydı korundu");
            LOGGER.info("  - " + supersededCount + " eski seçim elendi ve AVAILABLE statüsüne çekildi");

        } catch (SQLException e) {
            connection.rollback();
            LOGGER.severe("Migration V1 başarısız oldu, veritabanı geri alındı: " + e.getMessage());
            throw e;
        } finally {
            connection.setAutoCommit(autoCommit);
        }
    }

    private static int resolveDuplicatePetId(Connection connection, String petId) throws SQLException {
        String query = "SELECT owner_id, updated_at FROM player_active_pets WHERE pet_id = ? ORDER BY updated_at DESC, owner_id ASC;";
        record SelectionRecord(String ownerId, long updatedAt) {}
        List<SelectionRecord> records = new ArrayList<>();

        try (PreparedStatement ps = connection.prepareStatement(query)) {
            ps.setString(1, petId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    records.add(new SelectionRecord(rs.getString("owner_id"), rs.getLong("updated_at")));
                }
            }
        }

        int removed = 0;
        if (records.size() > 1) {
            SelectionRecord kept = records.get(0);
            LOGGER.warning("Çakışan Pet ID '" + petId + "' için Owner '" + kept.ownerId + "' seçimi korundu (Timestamp: " + kept.updatedAt + ").");

            for (int i = 1; i < records.size(); i++) {
                SelectionRecord dropped = records.get(i);
                LOGGER.warning("Çakışan Pet ID '" + petId + "' için Owner '" + dropped.ownerId + "' eski seçimi siliniyor ve AVAILABLE yapılıyor (Timestamp: " + dropped.updatedAt + ").");
                
                // Delete superseded selection row
                try (PreparedStatement ps = connection.prepareStatement("DELETE FROM player_active_pets WHERE owner_id = ? AND pet_id = ?;")) {
                    ps.setString(1, dropped.ownerId);
                    ps.setString(2, petId);
                    ps.executeUpdate();
                }

                // Update pet state to AVAILABLE if state was ACTIVE
                try (PreparedStatement ps = connection.prepareStatement("UPDATE pets SET state = 'AVAILABLE' WHERE pet_id = ? AND owner_id = ?;")) {
                    ps.setString(1, petId);
                    ps.setString(2, dropped.ownerId);
                    ps.executeUpdate();
                }

                removed++;
            }
        }
        return removed;
    }
}

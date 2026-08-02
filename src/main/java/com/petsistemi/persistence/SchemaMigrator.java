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
                recordMigrationApplied(connection, 1);
            }

            if (!isMigrationApplied(connection, 2)) {
                applyMigrationV2(connection);
            }

            if (!isMigrationApplied(connection, 3)) {
                applyMigrationV3(connection);
            }

            if (!isMigrationApplied(connection, 4)) {
                applyMigrationV4StateReconciliation(connection);
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

    private static void recordMigrationApplied(Connection connection, int version) throws SQLException {
        String sql = "INSERT INTO schema_migrations (version, applied_at) VALUES (?, ?);";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, version);
            ps.setLong(2, System.currentTimeMillis());
            ps.executeUpdate();
        }
    }

    private static void applyMigrationV2(Connection connection) throws SQLException {
        boolean autoCommit = connection.getAutoCommit();
        try {
            connection.setAutoCommit(false);
            
            int totalInspected = 0;
            try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM player_active_pets;")) {
                if (rs.next()) {
                    totalInspected = rs.getInt(1);
                }
            }

            List<String> allSelectedPetIds = new ArrayList<>();
            String checkSelectedSql = "SELECT DISTINCT pet_id FROM player_active_pets;";
            try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(checkSelectedSql)) {
                while (rs.next()) {
                    allSelectedPetIds.add(rs.getString("pet_id"));
                }
            }

            int supersededCount = 0;
            if (!allSelectedPetIds.isEmpty()) {
                for (String petId : allSelectedPetIds) {
                    supersededCount += resolveOwnershipAndDuplicates(connection, petId);
                }
            }

            recordMigrationApplied(connection, 2);
            connection.commit();

            int retainedCount = totalInspected - supersededCount;
            LOGGER.info("[PetSistemi] Migration V2 (Ownership Clean) Raporu:");
            LOGGER.info("  - " + totalInspected + " seçim kaydı incelendi");
            LOGGER.info("  - " + allSelectedPetIds.size() + " aktif pet seçimi incelendi");
            LOGGER.info("  - " + retainedCount + " gerçek sahibi doğrulanan seçim korundu");
            LOGGER.info("  - " + supersededCount + " geçersiz/yabancı seçim silindi");

        } catch (SQLException e) {
            connection.rollback();
            LOGGER.severe("Migration V2 başarısız oldu, veritabanı geri alındı: " + e.getMessage());
            throw e;
        } finally {
            connection.setAutoCommit(autoCommit);
        }
    }

    private static void applyMigrationV3(Connection connection) throws SQLException {
        boolean autoCommit = connection.getAutoCommit();
        try {
            connection.setAutoCommit(false);

            try (Statement stmt = connection.createStatement()) {
                stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS uq_pets_pet_owner ON pets(pet_id, owner_id);");

                // Clean temporary table if exists from aborted attempts
                stmt.execute("DROP TABLE IF EXISTS player_active_pets_v3;");

                stmt.execute("CREATE TABLE player_active_pets_v3 (" +
                        "owner_id TEXT PRIMARY KEY, " +
                        "pet_id TEXT NOT NULL UNIQUE, " +
                        "updated_at INTEGER NOT NULL, " +
                        "FOREIGN KEY (pet_id, owner_id) REFERENCES pets(pet_id, owner_id) ON DELETE CASCADE" +
                        ");");

                int sourceCount = 0;
                try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM player_active_pets;")) {
                    if (rs.next()) {
                        sourceCount = rs.getInt(1);
                    }
                }

                stmt.execute("INSERT INTO player_active_pets_v3 (owner_id, pet_id, updated_at) " +
                        "SELECT a.owner_id, a.pet_id, a.updated_at FROM player_active_pets a " +
                        "JOIN pets p ON a.pet_id = p.pet_id AND a.owner_id = p.owner_id;");

                int destCount = 0;
                try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM player_active_pets_v3;")) {
                    if (rs.next()) {
                        destCount = rs.getInt(1);
                    }
                }

                if (sourceCount != destCount) {
                    throw new SQLException("Migration V3 satır sayısı uyuşmazlığı! Beklenen kaynak: " + sourceCount + ", aktarılan hedef: " + destCount);
                }

                stmt.execute("DROP TABLE IF EXISTS player_active_pets;");
                stmt.execute("ALTER TABLE player_active_pets_v3 RENAME TO player_active_pets;");
            }

            recordMigrationApplied(connection, 3);
            connection.commit();
            LOGGER.info("[PetSistemi] Migration V3 (Composite Foreign Key Constraint) başarıyla uygulandı.");

        } catch (SQLException e) {
            connection.rollback();
            LOGGER.severe("Migration V3 başarısız oldu: " + e.getMessage());
            throw e;
        } finally {
            connection.setAutoCommit(autoCommit);
        }
    }

    private static void applyMigrationV4StateReconciliation(Connection connection) throws SQLException {
        boolean autoCommit = connection.getAutoCommit();
        try {
            connection.setAutoCommit(false);

            try (Statement stmt = connection.createStatement()) {
                // 1. Delete all active selection records for pets that are DISABLED
                stmt.execute("DELETE FROM player_active_pets WHERE pet_id IN (" +
                        "SELECT pet_id FROM pets WHERE state = 'DISABLED'" +
                        ");");

                // 2. Delete any imposter selection records where (pet_id, owner_id) pair doesn't exist in pets
                stmt.execute("DELETE FROM player_active_pets WHERE NOT EXISTS (" +
                        "SELECT 1 FROM pets p WHERE p.pet_id = player_active_pets.pet_id AND p.owner_id = player_active_pets.owner_id" +
                        ");");

                // 3. Convert unselected ACTIVE pets to AVAILABLE (does NOT touch DISABLED pets)
                stmt.execute("UPDATE pets SET state = 'AVAILABLE' WHERE state = 'ACTIVE' AND NOT EXISTS (" +
                        "SELECT 1 FROM player_active_pets active WHERE active.pet_id = pets.pet_id AND active.owner_id = pets.owner_id" +
                        ");");

                // 4. Convert selected AVAILABLE pets to ACTIVE (does NOT touch DISABLED pets)
                stmt.execute("UPDATE pets SET state = 'ACTIVE' WHERE state = 'AVAILABLE' AND EXISTS (" +
                        "SELECT 1 FROM player_active_pets active WHERE active.pet_id = pets.pet_id AND active.owner_id = pets.owner_id" +
                        ");");
            }

            recordMigrationApplied(connection, 4);
            connection.commit();
            LOGGER.info("[PetSistemi] Migration V4 (State Reconciliation & Upgrade Hardening) başarıyla uygulandı.");

        } catch (SQLException e) {
            connection.rollback();
            LOGGER.severe("Migration V4 başarısız oldu: " + e.getMessage());
            throw e;
        } finally {
            connection.setAutoCommit(autoCommit);
        }
    }

    private static int resolveOwnershipAndDuplicates(Connection connection, String petId) throws SQLException {
        String findPetSql = "SELECT owner_id, state FROM pets WHERE pet_id = ?;";
        String actualOwnerId = null;
        String currentState = null;

        try (PreparedStatement ps = connection.prepareStatement(findPetSql)) {
            ps.setString(1, petId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    actualOwnerId = rs.getString("owner_id");
                    currentState = rs.getString("state");
                }
            }
        }

        String querySelections = "SELECT owner_id, updated_at FROM player_active_pets WHERE pet_id = ?;";
        record SelectionRecord(String ownerId, long updatedAt) {}
        List<SelectionRecord> records = new ArrayList<>();

        try (PreparedStatement ps = connection.prepareStatement(querySelections)) {
            ps.setString(1, petId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    records.add(new SelectionRecord(rs.getString("owner_id"), rs.getLong("updated_at")));
                }
            }
        }

        int removedCount = 0;

        // 1. If Pet state is DISABLED, delete all selection records and keep DISABLED
        if ("DISABLED".equalsIgnoreCase(currentState)) {
            LOGGER.warning("Pet ID '" + petId + "' DISABLED durumunda. Tüm seçim kayıtları siliniyor ve state DISABLED kalıyor.");
            for (SelectionRecord record : records) {
                try (PreparedStatement ps = connection.prepareStatement("DELETE FROM player_active_pets WHERE owner_id = ? AND pet_id = ?;")) {
                    ps.setString(1, record.ownerId());
                    ps.setString(2, petId);
                    ps.executeUpdate();
                }
                removedCount++;
            }
            return removedCount;
        }

        // 2. Pet is AVAILABLE or ACTIVE: verify actual owner
        final String finalActualOwnerId = actualOwnerId;
        SelectionRecord validRecord = records.stream()
                .filter(r -> r.ownerId().equals(finalActualOwnerId))
                .findFirst()
                .orElse(null);

        if (validRecord != null) {
            LOGGER.warning("Pet ID '" + petId + "' için gerçek sahibi olan Owner '" + validRecord.ownerId() + "' seçimi korundu.");

            for (SelectionRecord record : records) {
                if (!record.ownerId().equals(validRecord.ownerId())) {
                    LOGGER.warning("Pet ID '" + petId + "' için yabancı oyuncu Owner '" + record.ownerId() + "' sahte seçimi siliniyor.");
                    try (PreparedStatement ps = connection.prepareStatement("DELETE FROM player_active_pets WHERE owner_id = ? AND pet_id = ?;")) {
                        ps.setString(1, record.ownerId());
                        ps.setString(2, petId);
                        ps.executeUpdate();
                    }
                    removedCount++;
                }
            }

            try (PreparedStatement ps = connection.prepareStatement("UPDATE pets SET state = 'ACTIVE' WHERE pet_id = ?;")) {
                ps.setString(1, petId);
                ps.executeUpdate();
            }

        } else {
            LOGGER.warning("Pet ID '" + petId + "' için gerçek sahibine ait aktif seçim bulunamadı. Tüm sahte seçimler siliniyor.");
            for (SelectionRecord record : records) {
                try (PreparedStatement ps = connection.prepareStatement("DELETE FROM player_active_pets WHERE owner_id = ? AND pet_id = ?;")) {
                    ps.setString(1, record.ownerId());
                    ps.setString(2, petId);
                    ps.executeUpdate();
                }
                removedCount++;
            }

            try (PreparedStatement ps = connection.prepareStatement("UPDATE pets SET state = 'AVAILABLE' WHERE pet_id = ?;")) {
                ps.setString(1, petId);
                ps.executeUpdate();
            }
        }

        return removedCount;
    }
}

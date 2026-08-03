package com.petsistemi.persistence.migration;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

public class MigrationRunner {

    private final Logger logger;
    private final List<DatabaseMigration> migrations;
    private final MigrationBackupManager backupManager;

    public MigrationRunner(Logger logger, List<DatabaseMigration> migrations, MigrationBackupManager backupManager) {
        this.logger = logger;
        this.migrations = new ArrayList<>(migrations);
        this.backupManager = backupManager;
        validateNoDuplicates();
        this.migrations.sort(Comparator.comparingInt(DatabaseMigration::version));
    }

    private void validateNoDuplicates() {
        Set<Integer> versions = new HashSet<>();
        for (DatabaseMigration m : migrations) {
            if (!versions.add(m.version())) {
                throw new IllegalStateException("Duplicate migration version detected: V" + m.version());
            }
        }
    }

    public void run(Connection connection, File dbFile, File backupDir, boolean backupEnabled, boolean failOnBackupError, int maxBackups) throws SQLException {
        ensureSchemaMigrationsTable(connection);

        Set<Integer> appliedVersions = getAppliedVersions(connection);
        boolean needsLegacyV2Repair = appliedVersions.contains(2) && !appliedVersions.contains(3);

        List<DatabaseMigration> pending = new ArrayList<>();
        for (DatabaseMigration m : migrations) {
            if (!appliedVersions.contains(m.version())) {
                pending.add(m);
            }
        }

        if (pending.isEmpty() && !needsLegacyV2Repair) {
            logger.info("Veritabanı en güncel sürümde (" + getLatestAppliedVersion(appliedVersions) + "). Migration gerekmiyor.");
            return;
        }

        int targetVersion = pending.isEmpty() ? 2 : pending.get(pending.size() - 1).version();

        // 1. Take WAL-safe backup BEFORE modifying any data
        if (dbFile != null && backupDir != null) {
            try {
                backupManager.createBackup(connection, dbFile, backupDir, targetVersion, backupEnabled, failOnBackupError, maxBackups);
            } catch (Exception e) {
                if (failOnBackupError) {
                    throw new SQLException("Migration öncesi yedekleme başarısız olduğu için durduruldu: " + e.getMessage(), e);
                }
            }
        }

        logger.info("Uygulanacak " + pending.size() + " adet migration bulundu. Çalıştırılıyor...");
        
        try (Statement pragmaStmt = connection.createStatement()) {
            pragmaStmt.execute("PRAGMA foreign_keys = OFF;");
        }

        boolean autoCommit = connection.getAutoCommit();
        try {
            connection.setAutoCommit(false);

            // 2. Perform Legacy Repair INSIDE transaction after backup
            if (needsLegacyV2Repair) {
                performLegacyV2Repair(connection);
            }

            for (DatabaseMigration migration : pending) {
                applySingleMigration(connection, migration);
            }

            // 3. Final Foreign Key Integrity Check BEFORE Commit
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery("PRAGMA foreign_key_check;")) {
                if (rs.next()) {
                    String table = rs.getString(1);
                    String rowid = rs.getString(2);
                    String parent = rs.getString(3);
                    throw new SQLException("Migration sonu Yabancı Anahtar Doğrulaması Başarısız! Tablo: " + table + ", RowID: " + rowid + ", Parent: " + parent);
                }
            }

            connection.commit();
        } catch (Throwable failure) {
            try {
                connection.rollback();
            } catch (SQLException rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            }
            if (failure instanceof SQLException sql) {
                throw sql;
            }
            throw new SQLException("Migration beklenmeyen hata nedeniyle başarısız oldu: " + failure.getMessage(), failure);
        } finally {
            connection.setAutoCommit(autoCommit);
            try (Statement pragmaStmt = connection.createStatement()) {
                pragmaStmt.execute("PRAGMA foreign_keys = ON;");
                try (ResultSet rs = pragmaStmt.executeQuery("PRAGMA foreign_keys;")) {
                    if (rs.next() && rs.getInt(1) != 1) {
                        logger.warning("FOREIGN KEY enforcement re-enable edilemedi!");
                    }
                }
            }
        }
    }

    private void performLegacyV2Repair(Connection connection) throws SQLException {
        logger.info("Legacy V2 upgrade repair kontrolü çalıştırılıyor...");
        String sql = "DELETE FROM player_active_pets WHERE ROWID IN (" +
                     "  SELECT pap.ROWID FROM player_active_pets pap " +
                     "  LEFT JOIN pets p ON pap.pet_id = p.pet_id AND pap.owner_id = p.owner_id " +
                     "  WHERE p.pet_id IS NULL" +
                     ");";
        try (Statement stmt = connection.createStatement()) {
            int deleted = stmt.executeUpdate(sql);
            if (deleted > 0) {
                logger.info("Legacy V2 repair tamamlandı: " + deleted + " adet sahte (imposter) aktif pet seçimi temizlendi.");
            }
        }
    }

    private void applySingleMigration(Connection connection, DatabaseMigration migration) throws SQLException {
        logger.info("Migration V" + migration.version() + " (" + migration.name() + ") uygulanıyor...");

        migration.apply(connection);

        recordMigration(connection, migration.version());

        logger.info("Migration V" + migration.version() + " başarıyla tamamlandı.");
    }

    private void ensureSchemaMigrationsTable(Connection connection) throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS schema_migrations (" +
                    "version INTEGER PRIMARY KEY, " +
                    "applied_at INTEGER NOT NULL" +
                    ");");
        }
    }

    private Set<Integer> getAppliedVersions(Connection connection) throws SQLException {
        Set<Integer> set = new HashSet<>();
        String sql = "SELECT version FROM schema_migrations;";
        try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                set.add(rs.getInt("version"));
            }
        }
        return set;
    }

    private void recordMigration(Connection connection, int version) throws SQLException {
        String sql = "INSERT INTO schema_migrations (version, applied_at) VALUES (?, ?);";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, version);
            ps.setLong(2, System.currentTimeMillis());
            ps.executeUpdate();
        }
    }

    private int getLatestAppliedVersion(Set<Integer> versions) {
        return versions.stream().max(Integer::compareTo).orElse(0);
    }
}

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
        List<DatabaseMigration> pending = new ArrayList<>();
        for (DatabaseMigration m : migrations) {
            if (!appliedVersions.contains(m.version())) {
                pending.add(m);
            }
        }

        if (pending.isEmpty()) {
            logger.info("Veritabanı en güncel sürümde (" + getLatestAppliedVersion(appliedVersions) + "). Migration gerekmiyor.");
            return;
        }

        int targetVersion = pending.get(pending.size() - 1).version();
        if (dbFile != null && backupDir != null) {
            try {
                backupManager.createBackup(dbFile, backupDir, targetVersion, backupEnabled, failOnBackupError, maxBackups);
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
            for (DatabaseMigration migration : pending) {
                applySingleMigration(connection, migration);
            }
            connection.commit();
        } catch (SQLException e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(autoCommit);
            try (Statement pragmaStmt = connection.createStatement()) {
                pragmaStmt.execute("PRAGMA foreign_keys = ON;");
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

package com.petsistemi.persistence;

import com.petsistemi.persistence.migration.DatabaseMigration;
import com.petsistemi.persistence.migration.MigrationBackupManager;
import com.petsistemi.persistence.migration.MigrationRunner;
import com.petsistemi.persistence.migration.V1InitialSchemaMigration;
import com.petsistemi.persistence.migration.V2UniqueSelectedPetMigration;
import com.petsistemi.persistence.migration.V3CompositeForeignKeyMigration;
import com.petsistemi.persistence.migration.V4StateReconciliationMigration;
import com.petsistemi.persistence.migration.V5AvailabilityStateMigration;
import com.petsistemi.persistence.migration.V6SelectionTableRenameMigration;
import com.petsistemi.persistence.migration.V7AuditLogTableMigration;
import com.petsistemi.persistence.migration.V8FollowModeMigration;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.logging.Logger;

public final class SchemaMigrator {

    private static final Logger LOGGER = Logger.getLogger(SchemaMigrator.class.getName());

    private SchemaMigrator() {}

    public static List<DatabaseMigration> getStandardMigrations() {
        return List.of(
                new V1InitialSchemaMigration(),
                new V2UniqueSelectedPetMigration(),
                new V3CompositeForeignKeyMigration(),
                new V4StateReconciliationMigration(),
                new V5AvailabilityStateMigration(),
                new V6SelectionTableRenameMigration(),
                new V7AuditLogTableMigration(),
                new V8FollowModeMigration()
        );
    }

    public static void migrate(Connection connection) throws SQLException {
        migrate(connection, null, null, false, false, 5);
    }

    public static void migrate(Connection connection, File dbFile, File backupDir, boolean backupEnabled, boolean failOnBackupError, int maxBackups) throws SQLException {
        MigrationRunner runner = new MigrationRunner(LOGGER, getStandardMigrations(), new MigrationBackupManager(LOGGER));
        runner.run(connection, dbFile, backupDir, backupEnabled, failOnBackupError, maxBackups);
    }
}

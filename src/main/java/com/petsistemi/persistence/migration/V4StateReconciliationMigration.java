package com.petsistemi.persistence.migration;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Logger;

public class V4StateReconciliationMigration implements DatabaseMigration {

    private static final Logger LOGGER = Logger.getLogger(V4StateReconciliationMigration.class.getName());

    @Override
    public int version() {
        return 4;
    }

    @Override
    public String name() {
        return "State Reconciliation and Upgrade Hardening";
    }

    @Override
    public void apply(Connection connection) throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            // 1. Delete all active selection records for DISABLED pets
            int deletedDisabledSelections = stmt.executeUpdate(
                    "DELETE FROM player_active_pets WHERE pet_id IN (" +
                            "SELECT pet_id FROM pets WHERE state = 'DISABLED'" +
                            ");"
            );
            if (deletedDisabledSelections > 0) {
                LOGGER.info("V4 Migration: " + deletedDisabledSelections + " adet DISABLED pet seçimi temizlendi.");
            }

            // 2. Delete imposter selection records where (pet_id, owner_id) pair doesn't match pets table
            int deletedImposterSelections = stmt.executeUpdate(
                    "DELETE FROM player_active_pets WHERE NOT EXISTS (" +
                            "SELECT 1 FROM pets p WHERE p.pet_id = player_active_pets.pet_id AND p.owner_id = player_active_pets.owner_id" +
                            ");"
            );
            if (deletedImposterSelections > 0) {
                LOGGER.info("V4 Migration: " + deletedImposterSelections + " adet geçersiz/sahte owner seçimi temizlendi.");
            }

            // 3. Convert unselected ACTIVE pets to AVAILABLE
            int reconciledToAvailable = stmt.executeUpdate(
                    "UPDATE pets SET state = 'AVAILABLE' WHERE state = 'ACTIVE' AND NOT EXISTS (" +
                            "SELECT 1 FROM player_active_pets active WHERE active.pet_id = pets.pet_id AND active.owner_id = pets.owner_id" +
                            ");"
            );
            if (reconciledToAvailable > 0) {
                LOGGER.info("V4 Migration: " + reconciledToAvailable + " adet seçimsiz ACTIVE pet AVAILABLE yapıldı.");
            }

            // 4. Convert selected AVAILABLE pets to ACTIVE
            int reconciledToActive = stmt.executeUpdate(
                    "UPDATE pets SET state = 'ACTIVE' WHERE state = 'AVAILABLE' AND EXISTS (" +
                            "SELECT 1 FROM player_active_pets active WHERE active.pet_id = pets.pet_id AND active.owner_id = pets.owner_id" +
                            ");"
            );
            if (reconciledToActive > 0) {
                LOGGER.info("V4 Migration: " + reconciledToActive + " adet seçili AVAILABLE pet ACTIVE yapıldı.");
            }

            // 5. Run PRAGMA foreign_key_check
            try (ResultSet rs = stmt.executeQuery("PRAGMA foreign_key_check;")) {
                if (rs.next()) {
                    String table = rs.getString("table");
                    String parent = rs.getString("parent");
                    throw new SQLException("Foreign key check hatası! Tablo: " + table + ", Ebeveyn: " + parent);
                }
            }
        }
    }
}

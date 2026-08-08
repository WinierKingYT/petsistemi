package com.petsistemi.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.logging.Logger;

/** MySQL adapter; portable CRUD/transactions are inherited, only SQLite upserts differ. */
public final class MysqlPetRepository extends SqlitePetRepository {
    private final ConnectionProvider provider;
    private final Logger logger;

    public MysqlPetRepository(ConnectionProvider provider, Logger logger) {
        super(provider, logger);
        this.provider = provider;
        this.logger = logger;
    }

    @Override public synchronized void setActivePet(UUID ownerId, UUID petId) {
        DatabaseThreadGuard.requireDatabaseThread();
        upsert(ownerId, petId, provider.getConnection());
    }

    @Override public synchronized void switchActivePet(UUID ownerId, UUID previousPetId, UUID newPetId) {
        DatabaseThreadGuard.requireDatabaseThread();
        Connection connection = provider.getConnection();
        boolean autoCommit = true;
        try {
            autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (PreparedStatement check = connection.prepareStatement(
                    "SELECT 1 FROM pets WHERE pet_id = ? AND owner_id = ?")) {
                check.setString(1, newPetId.toString());
                check.setString(2, ownerId.toString());
                try (ResultSet result = check.executeQuery()) {
                    if (!result.next()) throw new PetPersistenceException("Aktif yapılacak pet bulunamadı: " + newPetId);
                }
            }
            upsert(ownerId, newPetId, connection);
            connection.commit();
        } catch (Exception error) {
            try { connection.rollback(); } catch (SQLException rollback) { error.addSuppressed(rollback); }
            logger.severe("MySQL switchActivePet hatası: " + error.getMessage());
            throw error instanceof PetPersistenceException persistence ? persistence
                    : new PetPersistenceException("MySQL pet seçimi değiştirilemedi.", error);
        } finally {
            try { connection.setAutoCommit(autoCommit); } catch (SQLException ignored) {}
        }
    }

    @Override public synchronized void restoreActivePet(UUID ownerId, UUID previousPetId, UUID failedPetId) {
        DatabaseThreadGuard.requireDatabaseThread();
        Connection connection = provider.getConnection();
        boolean autoCommit = true;
        try {
            autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            if (previousPetId != null) {
                upsert(ownerId, previousPetId, connection);
            } else {
                try (PreparedStatement statement = connection.prepareStatement(
                        "DELETE FROM player_selected_pets WHERE owner_id = ?")) {
                    statement.setString(1, ownerId.toString());
                    statement.executeUpdate();
                }
            }
            connection.commit();
        } catch (Exception error) {
            try { connection.rollback(); } catch (SQLException rollback) { error.addSuppressed(rollback); }
            logger.severe("MySQL restoreActivePet hatası: " + error.getMessage());
            throw error instanceof PetPersistenceException persistence ? persistence
                    : new PetPersistenceException("MySQL eski pet seçimi geri yüklenemedi.", error);
        } finally {
            try { connection.setAutoCommit(autoCommit); } catch (SQLException ignored) {}
        }
    }

    private void upsert(UUID ownerId, UUID petId, Connection connection) {
        String sql = "INSERT INTO player_selected_pets(owner_id, pet_id, selected_at) VALUES (?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE pet_id = VALUES(pet_id), selected_at = VALUES(selected_at)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, ownerId.toString());
            statement.setString(2, petId.toString());
            statement.setLong(3, System.currentTimeMillis());
            statement.executeUpdate();
        } catch (SQLException error) {
            throw new PetPersistenceException("MySQL pet seçimi kaydedilemedi.", error);
        }
    }
}

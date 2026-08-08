package com.petsistemi.persistence;

import com.petsistemi.domain.PetFollowMode;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.logging.Logger;

public final class MysqlPetSelectionRepository extends SqlitePetSelectionRepository {
    private final ConnectionProvider provider;
    private final Logger logger;

    public MysqlPetSelectionRepository(ConnectionProvider provider, Logger logger) {
        super(provider, logger);
        this.provider = provider;
        this.logger = logger;
    }

    @Override public synchronized void select(UUID ownerId, UUID petId) {
        DatabaseThreadGuard.requireDatabaseThread();
        Connection connection = provider.getConnection();
        validateAvailable(connection, ownerId, petId);
        upsert(connection, ownerId, petId);
    }

    @Override public synchronized void switchSelection(UUID ownerId, UUID previousPetId, UUID newPetId) {
        DatabaseThreadGuard.requireDatabaseThread();
        Connection connection = provider.getConnection();
        boolean autoCommit = true;
        try {
            autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            validateAvailable(connection, ownerId, newPetId);
            upsert(connection, ownerId, newPetId);
            connection.commit();
        } catch (Exception error) {
            try { connection.rollback(); } catch (SQLException rollback) { error.addSuppressed(rollback); }
            logger.severe("MySQL switchSelection hatası: " + error.getMessage());
            throw error instanceof RuntimeException runtime ? runtime
                    : new PetPersistenceException("MySQL pet seçimi değiştirilemedi.", error);
        } finally {
            try { connection.setAutoCommit(autoCommit); } catch (SQLException ignored) {}
        }
    }

    private static void validateAvailable(Connection connection, UUID ownerId, UUID petId) {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT availability_state FROM pets WHERE pet_id = ? AND owner_id = ?")) {
            statement.setString(1, petId.toString());
            statement.setString(2, ownerId.toString());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) throw new IllegalArgumentException("Pet bulunamadı veya oyuncuya ait değil.");
                if ("DISABLED".equalsIgnoreCase(result.getString(1))) {
                    throw new IllegalArgumentException("Devre dışı bırakılmış petler seçilemez.");
                }
            }
        } catch (SQLException error) {
            throw new PetPersistenceException("Pet kullanılabilirliği doğrulanamadı.", error);
        }
    }

    private static void upsert(Connection connection, UUID ownerId, UUID petId) {
        String sql = "INSERT INTO player_selected_pets(owner_id, pet_id, selected_at, follow_mode) VALUES (?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE pet_id = VALUES(pet_id), selected_at = VALUES(selected_at)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, ownerId.toString());
            statement.setString(2, petId.toString());
            statement.setLong(3, System.currentTimeMillis());
            statement.setString(4, PetFollowMode.FOLLOW.name());
            statement.executeUpdate();
        } catch (SQLException error) {
            throw new PetPersistenceException("MySQL pet seçimi kaydedilemedi.", error);
        }
    }
}

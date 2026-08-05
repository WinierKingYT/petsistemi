package com.petsistemi.persistence;

import com.petsistemi.domain.PetSelection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

public class SqlitePetSelectionRepository implements PetSelectionRepository {

    private final ConnectionProvider connectionProvider;
    private final Logger logger;

    public SqlitePetSelectionRepository(ConnectionProvider connectionProvider, Logger logger) {
        this.connectionProvider = connectionProvider;
        this.logger = logger;
    }

    @Override
    public synchronized Optional<PetSelection> findByOwner(UUID ownerId) {
        DatabaseThreadGuard.requireDatabaseThread();
        String sql = "SELECT * FROM player_selected_pets WHERE owner_id = ?;";
        try (PreparedStatement ps = connectionProvider.getConnection().prepareStatement(sql)) {
            ps.setString(1, ownerId.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(new PetSelection(
                            UUID.fromString(rs.getString("owner_id")),
                            UUID.fromString(rs.getString("pet_id")),
                            rs.getLong("selected_at")
                    ));
                }
            }
        } catch (SQLException e) {
            logger.severe("findByOwner selection sorgusunda hata: " + e.getMessage());
            throw new PetPersistenceException("Pet seçimi sorgulanamadı.", e);
        }
        return Optional.empty();
    }

    @Override
    public synchronized void select(UUID ownerId, UUID petId) {
        DatabaseThreadGuard.requireDatabaseThread();
        String checkSql = "SELECT availability_state FROM pets WHERE pet_id = ? AND owner_id = ?;";
        try (PreparedStatement checkPs = connectionProvider.getConnection().prepareStatement(checkSql)) {
            checkPs.setString(1, petId.toString());
            checkPs.setString(2, ownerId.toString());
            try (ResultSet rs = checkPs.executeQuery()) {
                if (rs.next()) {
                    String state = rs.getString("availability_state");
                    if ("DISABLED".equalsIgnoreCase(state)) {
                        throw new IllegalArgumentException("Devre dışı bırakılmış petler seçilemez.");
                    }
                } else {
                    throw new IllegalArgumentException("Pet bulunamadı veya oyuncuya ait değil.");
                }
            }
        } catch (SQLException e) {
            logger.severe("select kullanılabilirlik sorgusunda veritabanı hatası: " + e.getMessage());
            throw new PetPersistenceException("Pet kullanılabilirlik durumu doğrulanamadı.", e);
        }

        String sql = "INSERT INTO player_selected_pets (owner_id, pet_id, selected_at) VALUES (?, ?, ?) " +
                "ON CONFLICT(owner_id) DO UPDATE SET pet_id = excluded.pet_id, selected_at = excluded.selected_at;";
        try (PreparedStatement ps = connectionProvider.getConnection().prepareStatement(sql)) {
            ps.setString(1, ownerId.toString());
            ps.setString(2, petId.toString());
            ps.setLong(3, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.severe("select sorgusunda veritabanı hatası: " + e.getMessage());
            throw new PetPersistenceException("Pet seçimi veritabanına kaydedilemedi.", e);
        }
    }

    @Override
    public synchronized void clear(UUID ownerId) {
        DatabaseThreadGuard.requireDatabaseThread();
        String sql = "DELETE FROM player_selected_pets WHERE owner_id = ?;";
        try (PreparedStatement ps = connectionProvider.getConnection().prepareStatement(sql)) {
            ps.setString(1, ownerId.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.severe("clear selection sorgusunda hata: " + e.getMessage());
            throw new PetPersistenceException("Pet seçimi temizlenemedi.", e);
        }
    }

    @Override
    public synchronized void switchSelection(UUID ownerId, UUID previousPetId, UUID newPetId) {
        DatabaseThreadGuard.requireDatabaseThread();
        Connection conn = connectionProvider.getConnection();
        boolean autoCommit = true;
        try {
            autoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);

            String checkSql = "SELECT availability_state FROM pets WHERE pet_id = ? AND owner_id = ?;";
            try (PreparedStatement checkPs = conn.prepareStatement(checkSql)) {
                checkPs.setString(1, newPetId.toString());
                checkPs.setString(2, ownerId.toString());
                try (ResultSet rs = checkPs.executeQuery()) {
                    if (rs.next()) {
                        String state = rs.getString("availability_state");
                        if ("DISABLED".equalsIgnoreCase(state)) {
                            throw new IllegalArgumentException("Devre dışı bırakılmış petler seçilemez.");
                        }
                    } else {
                        throw new IllegalArgumentException("Pet bulunamadı veya oyuncuya ait değil.");
                    }
                }
            }

            try (PreparedStatement ps = conn.prepareStatement("INSERT INTO player_selected_pets (owner_id, pet_id, selected_at) VALUES (?, ?, ?) " +
                    "ON CONFLICT(owner_id) DO UPDATE SET pet_id = excluded.pet_id, selected_at = excluded.selected_at;")) {
                ps.setString(1, ownerId.toString());
                ps.setString(2, newPetId.toString());
                ps.setLong(3, System.currentTimeMillis());
                ps.executeUpdate();
            }

            conn.commit();
        } catch (Exception e) {
            try {
                conn.rollback();
            } catch (SQLException rollbackEx) {
                logger.severe("switchSelection rollback hatası: " + rollbackEx.getMessage());
            }
            logger.severe("switchSelection transaction hatası: " + e.getMessage());
            throw new PetPersistenceException("Pet seçimi değiştirme transaction hatası.", e);
        } finally {
            try {
                conn.setAutoCommit(autoCommit);
            } catch (SQLException acEx) {
                logger.severe("switchSelection autoCommit restore hatası: " + acEx.getMessage());
            }
        }
    }
}

package com.petsistemi.persistence;

import com.petsistemi.domain.PetInstance;
import com.petsistemi.domain.PetStorageState;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

public class SqlitePetRepository implements PetRepository {

    private final DatabaseManager dbManager;
    private final Logger logger;

    public SqlitePetRepository(DatabaseManager dbManager, Logger logger) {
        this.dbManager = dbManager;
        this.logger = logger;
    }

    @Override
    public Optional<PetInstance> findById(UUID petId) {
        String sql = "SELECT * FROM pets WHERE pet_id = ?;";
        try (PreparedStatement ps = dbManager.getConnection().prepareStatement(sql)) {
            ps.setString(1, petId.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToPetInstance(rs));
                }
            }
        } catch (SQLException e) {
            logger.severe("findById sorgusunda veritabanı hatası: " + e.getMessage());
            throw new PetPersistenceException("Pet verisi sorgulanamadı.", e);
        }
        return Optional.empty();
    }

    @Override
    public List<PetInstance> findByOwner(UUID ownerId) {
        List<PetInstance> list = new ArrayList<>();
        String sql = "SELECT * FROM pets WHERE owner_id = ?;";
        try (PreparedStatement ps = dbManager.getConnection().prepareStatement(sql)) {
            ps.setString(1, ownerId.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSetToPetInstance(rs));
                }
            }
        } catch (SQLException e) {
            logger.severe("findByOwner sorgusunda veritabanı hatası: " + e.getMessage());
            throw new PetPersistenceException("Oyuncu petleri sorgulanamadı.", e);
        }
        return list;
    }

    @Override
    public Optional<PetInstance> findActiveByOwner(UUID ownerId) {
        String sql = "SELECT p.* FROM pets p JOIN player_active_pets a ON p.pet_id = a.pet_id WHERE a.owner_id = ?;";
        try (PreparedStatement ps = dbManager.getConnection().prepareStatement(sql)) {
            ps.setString(1, ownerId.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToPetInstance(rs));
                }
            }
        } catch (SQLException e) {
            logger.severe("findActiveByOwner sorgusunda veritabanı hatası: " + e.getMessage());
            throw new PetPersistenceException("Aktif pet verisi sorgulanamadı.", e);
        }
        return Optional.empty();
    }

    @Override
    public void insert(PetInstance pet) {
        String sql = "INSERT INTO pets (pet_id, owner_id, definition_id, custom_name, level, experience, state, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?);";
        try (PreparedStatement ps = dbManager.getConnection().prepareStatement(sql)) {
            ps.setString(1, pet.petId().toString());
            ps.setString(2, pet.ownerId().toString());
            ps.setString(3, pet.definitionId());
            ps.setString(4, pet.customName());
            ps.setInt(5, pet.level());
            ps.setLong(6, pet.experience());
            ps.setString(7, pet.storageState().name());
            ps.setLong(8, pet.createdAt());
            ps.setLong(9, pet.updatedAt());
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.severe("insert sorgusunda veritabanı hatası: " + e.getMessage());
            throw new PetPersistenceException("Pet veritabanına eklenemedi.", e);
        }
    }

    @Override
    public void update(PetInstance pet) {
        String sql = "UPDATE pets SET custom_name = ?, level = ?, experience = ?, state = ?, updated_at = ? WHERE pet_id = ?;";
        try (PreparedStatement ps = dbManager.getConnection().prepareStatement(sql)) {
            ps.setString(1, pet.customName());
            ps.setInt(2, pet.level());
            ps.setLong(3, pet.experience());
            ps.setString(4, pet.storageState().name());
            ps.setLong(5, pet.updatedAt());
            ps.setString(6, pet.petId().toString());
            int affected = ps.executeUpdate();
            if (affected == 0) {
                throw new PetPersistenceException("Güncellenecek pet kaydı veritabanında bulunamadı.");
            }
        } catch (SQLException e) {
            logger.severe("update sorgusunda veritabanı hatası: " + e.getMessage());
            throw new PetPersistenceException("Pet verisi güncellenemedi.", e);
        }
    }

    @Override
    public void delete(UUID petId) {
        String sql = "DELETE FROM pets WHERE pet_id = ?;";
        try (PreparedStatement ps = dbManager.getConnection().prepareStatement(sql)) {
            ps.setString(1, petId.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.severe("delete sorgusunda veritabanı hatası: " + e.getMessage());
            throw new PetPersistenceException("Pet veritabanından silinemedi.", e);
        }
    }

    @Override
    public void setActivePet(UUID ownerId, UUID petId) {
        String sql = "INSERT OR REPLACE INTO player_active_pets (owner_id, pet_id, updated_at) VALUES (?, ?, ?);";
        try (PreparedStatement ps = dbManager.getConnection().prepareStatement(sql)) {
            ps.setString(1, ownerId.toString());
            ps.setString(2, petId.toString());
            ps.setLong(3, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.severe("setActivePet sorgusunda veritabanı hatası: " + e.getMessage());
            throw new PetPersistenceException("Aktif pet veritabanına işlenemedi.", e);
        }
    }

    @Override
    public void clearActivePet(UUID ownerId) {
        String sql = "DELETE FROM player_active_pets WHERE owner_id = ?;";
        try (PreparedStatement ps = dbManager.getConnection().prepareStatement(sql)) {
            ps.setString(1, ownerId.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.severe("clearActivePet sorgusunda veritabanı hatası: " + e.getMessage());
            throw new PetPersistenceException("Aktif pet kaydı temizlenemedi.", e);
        }
    }

    @Override
    public synchronized void switchActivePet(UUID ownerId, UUID previousPetId, UUID newPetId) {
        Connection conn = dbManager.getConnection();
        try {
            conn.setAutoCommit(false);

            // 1. Reset previous pet state if present
            if (previousPetId != null) {
                try (PreparedStatement ps = conn.prepareStatement("UPDATE pets SET state = ? WHERE pet_id = ?;")) {
                    ps.setString(1, PetStorageState.AVAILABLE.name());
                    ps.setString(2, previousPetId.toString());
                    ps.executeUpdate();
                }
            }

            // 2. Insert or replace player_active_pets selection
            try (PreparedStatement ps = conn.prepareStatement("INSERT OR REPLACE INTO player_active_pets (owner_id, pet_id, updated_at) VALUES (?, ?, ?);")) {
                ps.setString(1, ownerId.toString());
                ps.setString(2, newPetId.toString());
                ps.setLong(3, System.currentTimeMillis());
                ps.executeUpdate();
            }

            // 3. Mark new pet as ACTIVE
            try (PreparedStatement ps = conn.prepareStatement("UPDATE pets SET state = ? WHERE pet_id = ?;")) {
                ps.setString(1, PetStorageState.ACTIVE.name());
                ps.setString(2, newPetId.toString());
                ps.executeUpdate();
            }

            conn.commit();
        } catch (Exception e) {
            try {
                conn.rollback();
            } catch (SQLException ignored) {}
            logger.severe("switchActivePet transaction hatası: " + e.getMessage());
            throw new PetPersistenceException("Pet değiştirme transaction işlemi başarısız.", e);
        } finally {
            try {
                conn.setAutoCommit(true);
            } catch (SQLException ignored) {}
        }
    }

    @Override
    public synchronized void clearActivePetAndSetAvailable(UUID ownerId, UUID petId) {
        Connection conn = dbManager.getConnection();
        try {
            conn.setAutoCommit(false);

            // 1. Delete player_active_pets row
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM player_active_pets WHERE owner_id = ?;")) {
                ps.setString(1, ownerId.toString());
                ps.executeUpdate();
            }

            // 2. Set pet state to AVAILABLE if petId is provided
            if (petId != null) {
                try (PreparedStatement ps = conn.prepareStatement("UPDATE pets SET state = ? WHERE pet_id = ?;")) {
                    ps.setString(1, PetStorageState.AVAILABLE.name());
                    ps.setString(2, petId.toString());
                    ps.executeUpdate();
                }
            }

            conn.commit();
        } catch (Exception e) {
            try {
                conn.rollback();
            } catch (SQLException ignored) {}
            logger.severe("clearActivePetAndSetAvailable transaction hatası: " + e.getMessage());
            throw new PetPersistenceException("Aktif pet temizleme transaction işlemi başarısız.", e);
        } finally {
            try {
                conn.setAutoCommit(true);
            } catch (SQLException ignored) {}
        }
    }

    private PetInstance mapResultSetToPetInstance(ResultSet rs) throws SQLException {
        return new PetInstance(
                UUID.fromString(rs.getString("pet_id")),
                UUID.fromString(rs.getString("owner_id")),
                rs.getString("definition_id"),
                rs.getString("custom_name"),
                rs.getInt("level"),
                rs.getLong("experience"),
                PetStorageState.valueOf(rs.getString("state")),
                rs.getLong("created_at"),
                rs.getLong("updated_at")
        );
    }
}

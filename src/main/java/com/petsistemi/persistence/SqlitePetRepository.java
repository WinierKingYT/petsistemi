package com.petsistemi.persistence;

import com.petsistemi.domain.PetInstance;
import com.petsistemi.domain.PetStorageState;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
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
        } catch (Exception e) {
            logger.log(Level.SEVERE, "findById sorgusunda hata oluştu!", e);
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
        } catch (Exception e) {
            logger.log(Level.SEVERE, "findByOwner sorgusunda hata oluştu!", e);
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
        } catch (Exception e) {
            logger.log(Level.SEVERE, "findActiveByOwner sorgusunda hata oluştu!", e);
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
        } catch (Exception e) {
            logger.log(Level.SEVERE, "insert sorgusunda hata oluştu!", e);
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
            ps.executeUpdate();
        } catch (Exception e) {
            logger.log(Level.SEVERE, "update sorgusunda hata oluştu!", e);
        }
    }

    @Override
    public void delete(UUID petId) {
        String sql = "DELETE FROM pets WHERE pet_id = ?;";
        try (PreparedStatement ps = dbManager.getConnection().prepareStatement(sql)) {
            ps.setString(1, petId.toString());
            ps.executeUpdate();
        } catch (Exception e) {
            logger.log(Level.SEVERE, "delete sorgusunda hata oluştu!", e);
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
        } catch (Exception e) {
            logger.log(Level.SEVERE, "setActivePet sorgusunda hata oluştu!", e);
        }
    }

    @Override
    public void clearActivePet(UUID ownerId) {
        String sql = "DELETE FROM player_active_pets WHERE owner_id = ?;";
        try (PreparedStatement ps = dbManager.getConnection().prepareStatement(sql)) {
            ps.setString(1, ownerId.toString());
            ps.executeUpdate();
        } catch (Exception e) {
            logger.log(Level.SEVERE, "clearActivePet sorgusunda hata oluştu!", e);
        }
    }

    private PetInstance mapResultSetToPetInstance(ResultSet rs) throws Exception {
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

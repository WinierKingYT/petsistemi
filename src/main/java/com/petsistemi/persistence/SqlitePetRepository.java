package com.petsistemi.persistence;

import com.petsistemi.domain.PetAvailabilityState;
import com.petsistemi.domain.PetInstance;

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

    private final ConnectionProvider connectionProvider;
    private final Logger logger;

    public SqlitePetRepository(ConnectionProvider connectionProvider, Logger logger) {
        this.connectionProvider = connectionProvider;
        this.logger = logger;
    }

    @Override
    public synchronized Optional<PetInstance> findById(UUID petId) {
        DatabaseThreadGuard.requireDatabaseThread();
        String sql = "SELECT * FROM pets WHERE pet_id = ?;";
        try (PreparedStatement ps = connectionProvider.getConnection().prepareStatement(sql)) {
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
    public synchronized List<PetInstance> findByOwner(UUID ownerId) {
        DatabaseThreadGuard.requireDatabaseThread();
        List<PetInstance> list = new ArrayList<>();
        String sql = "SELECT * FROM pets WHERE owner_id = ?;";
        try (PreparedStatement ps = connectionProvider.getConnection().prepareStatement(sql)) {
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
    public synchronized Optional<PetInstance> findActiveByOwner(UUID ownerId) {
        DatabaseThreadGuard.requireDatabaseThread();
        String sql = "SELECT p.* FROM pets p JOIN player_selected_pets s ON p.pet_id = s.pet_id AND p.owner_id = s.owner_id WHERE s.owner_id = ?;";
        try (PreparedStatement ps = connectionProvider.getConnection().prepareStatement(sql)) {
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
    public synchronized void insert(PetInstance pet) {
        DatabaseThreadGuard.requireDatabaseThread();
        String sql = "INSERT INTO pets (pet_id, owner_id, definition_id, custom_name, level, experience, availability_state, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?);";
        try (PreparedStatement ps = connectionProvider.getConnection().prepareStatement(sql)) {
            ps.setString(1, pet.petId().toString());
            ps.setString(2, pet.ownerId().toString());
            ps.setString(3, pet.definitionId());
            ps.setString(4, pet.customName());
            ps.setInt(5, pet.level());
            ps.setLong(6, pet.experience());
            ps.setString(7, pet.availabilityState().name());
            ps.setLong(8, pet.createdAt());
            ps.setLong(9, pet.updatedAt());
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.severe("insert sorgusunda veritabanı hatası: " + e.getMessage());
            throw new PetPersistenceException("Pet veritabanına eklenemedi.", e);
        }
    }

    @Override
    public synchronized void update(PetInstance pet) {
        DatabaseThreadGuard.requireDatabaseThread();
        String sql = "UPDATE pets SET custom_name = ?, level = ?, experience = ?, availability_state = ?, updated_at = ? WHERE pet_id = ?;";
        try (PreparedStatement ps = connectionProvider.getConnection().prepareStatement(sql)) {
            ps.setString(1, pet.customName());
            ps.setInt(2, pet.level());
            ps.setLong(3, pet.experience());
            ps.setString(4, pet.availabilityState().name());
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
    public synchronized void delete(UUID petId) {
        String sql = "DELETE FROM pets WHERE pet_id = ?;";
        try (PreparedStatement ps = connectionProvider.getConnection().prepareStatement(sql)) {
            ps.setString(1, petId.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.severe("delete sorgusunda veritabanı hatası: " + e.getMessage());
            throw new PetPersistenceException("Pet veritabanından silinemedi.", e);
        }
    }

    @Override
    public synchronized void setActivePet(UUID ownerId, UUID petId) {
        String sql = "INSERT INTO player_selected_pets (owner_id, pet_id, selected_at) VALUES (?, ?, ?) " +
                "ON CONFLICT(owner_id) DO UPDATE SET pet_id = excluded.pet_id, selected_at = excluded.selected_at;";
        try (PreparedStatement ps = connectionProvider.getConnection().prepareStatement(sql)) {
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
    public synchronized void clearActivePet(UUID ownerId) {
        String sql = "DELETE FROM player_selected_pets WHERE owner_id = ?;";
        try (PreparedStatement ps = connectionProvider.getConnection().prepareStatement(sql)) {
            ps.setString(1, ownerId.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.severe("clearActivePet sorgusunda veritabanı hatası: " + e.getMessage());
            throw new PetPersistenceException("Aktif pet kaydı temizlenemedi.", e);
        }
    }

    @Override
    public synchronized void switchActivePet(UUID ownerId, UUID previousPetId, UUID newPetId) {
        Connection conn = connectionProvider.getConnection();
        boolean autoCommit = true;
        try {
            autoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);

            try (PreparedStatement ps = conn.prepareStatement("INSERT INTO player_selected_pets (owner_id, pet_id, selected_at) VALUES (?, ?, ?) " +
                    "ON CONFLICT(owner_id) DO UPDATE SET pet_id = excluded.pet_id, selected_at = excluded.selected_at;")) {
                ps.setString(1, ownerId.toString());
                ps.setString(2, newPetId.toString());
                ps.setLong(3, System.currentTimeMillis());
                ps.executeUpdate();
            }

            try (PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM pets WHERE pet_id = ? AND owner_id = ?;")) {
                ps.setString(1, newPetId.toString());
                ps.setString(2, ownerId.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new PetPersistenceException("Aktif yapılacak pet veritabanında bulunamadı: " + newPetId);
                    }
                }
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
                conn.setAutoCommit(autoCommit);
            } catch (SQLException ignored) {}
        }
    }

    @Override
    public synchronized void clearActivePetAndSetAvailable(UUID ownerId, UUID petId) {
        clearActivePet(ownerId);
    }

    @Override
    public synchronized void restoreActivePet(UUID ownerId, UUID previousPetId, UUID failedPetId) {
        Connection conn = connectionProvider.getConnection();
        boolean autoCommit = true;
        try {
            autoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);

            if (previousPetId != null) {
                try (PreparedStatement ps = conn.prepareStatement("INSERT INTO player_selected_pets (owner_id, pet_id, selected_at) VALUES (?, ?, ?) " +
                        "ON CONFLICT(owner_id) DO UPDATE SET pet_id = excluded.pet_id, selected_at = excluded.selected_at;")) {
                    ps.setString(1, ownerId.toString());
                    ps.setString(2, previousPetId.toString());
                    ps.setLong(3, System.currentTimeMillis());
                    ps.executeUpdate();
                }
            } else {
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM player_selected_pets WHERE owner_id = ?;")) {
                    ps.setString(1, ownerId.toString());
                    ps.executeUpdate();
                }
            }

            conn.commit();
        } catch (Exception e) {
            try {
                conn.rollback();
            } catch (SQLException ignored) {}
            logger.severe("restoreActivePet transaction hatası: " + e.getMessage());
            throw new PetPersistenceException("Eski peti geri yükleme transaction işlemi başarısız.", e);
        } finally {
            try {
                conn.setAutoCommit(autoCommit);
            } catch (SQLException ignored) {}
        }
    }

    @Override
    public synchronized void disablePetTransactional(UUID ownerId, PetInstance updatedPet) {
        Connection conn = connectionProvider.getConnection();
        boolean autoCommit = true;
        try {
            autoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);

            if (ownerId != null) {
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM player_selected_pets WHERE owner_id = ? AND pet_id = ?;")) {
                    ps.setString(1, ownerId.toString());
                    ps.setString(2, updatedPet.petId().toString());
                    ps.executeUpdate();
                }
            }

            String sql = "UPDATE pets SET custom_name = ?, level = ?, experience = ?, availability_state = ?, updated_at = ? WHERE pet_id = ?;";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, updatedPet.customName());
                ps.setInt(2, updatedPet.level());
                ps.setLong(3, updatedPet.experience());
                ps.setString(4, updatedPet.availabilityState().name());
                ps.setLong(5, updatedPet.updatedAt());
                ps.setString(6, updatedPet.petId().toString());
                int affected = ps.executeUpdate();
                if (affected == 0) {
                    throw new PetPersistenceException("Güncellenecek pet kaydı veritabanında bulunamadı.");
                }
            }

            conn.commit();
        } catch (Exception e) {
            try {
                conn.rollback();
            } catch (SQLException ignored) {}
            logger.severe("disablePetTransactional hatası: " + e.getMessage());
            throw new PetPersistenceException("Pet devre dışı bırakma transaction işlemi başarısız.", e);
        } finally {
            try {
                conn.setAutoCommit(autoCommit);
            } catch (SQLException ignored) {}
        }
    }

    @Override
    public synchronized void removePetTransactional(UUID ownerId, UUID petId) {
        Connection conn = connectionProvider.getConnection();
        boolean autoCommit = true;
        try {
            autoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);

            if (ownerId != null) {
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM player_selected_pets WHERE owner_id = ? AND pet_id = ?;")) {
                    ps.setString(1, ownerId.toString());
                    ps.setString(2, petId.toString());
                    ps.executeUpdate();
                }
            }

            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM pets WHERE pet_id = ?;")) {
                ps.setString(1, petId.toString());
                ps.executeUpdate();
            }

            conn.commit();
        } catch (Exception e) {
            try {
                conn.rollback();
            } catch (SQLException ignored) {}
            logger.severe("removePetTransactional hatası: " + e.getMessage());
            throw new PetPersistenceException("Pet silme transaction işlemi başarısız.", e);
        } finally {
            try {
                conn.setAutoCommit(autoCommit);
            } catch (SQLException ignored) {}
        }
    }

    private PetInstance mapResultSetToPetInstance(ResultSet rs) throws SQLException {
        String rawState = rs.getString("availability_state");
        PetAvailabilityState availabilityState;
        try {
            availabilityState = PetAvailabilityState.valueOf(rawState);
        } catch (Exception e) {
            availabilityState = PetAvailabilityState.AVAILABLE;
        }

        return new PetInstance(
                UUID.fromString(rs.getString("pet_id")),
                UUID.fromString(rs.getString("owner_id")),
                rs.getString("definition_id"),
                rs.getString("custom_name"),
                rs.getInt("level"),
                rs.getLong("experience"),
                availabilityState,
                rs.getLong("created_at"),
                rs.getLong("updated_at")
        );
    }
}

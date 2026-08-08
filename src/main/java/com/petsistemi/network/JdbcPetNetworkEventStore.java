package com.petsistemi.network;

import com.petsistemi.persistence.ConnectionProvider;
import com.petsistemi.persistence.DatabaseThreadGuard;
import com.petsistemi.persistence.PetPersistenceException;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class JdbcPetNetworkEventStore {
    private final ConnectionProvider provider;

    public JdbcPetNetworkEventStore(ConnectionProvider provider) {
        this.provider = provider;
    }

    public void publish(String serverId, PetNetworkEventType type, UUID ownerId, UUID petId, String payload) {
        DatabaseThreadGuard.requireDatabaseThread();
        String sql = "INSERT INTO pet_network_events(server_id, event_type, owner_id, pet_id, payload, created_at) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = provider.getConnection().prepareStatement(sql)) {
            statement.setString(1, serverId);
            statement.setString(2, type.name());
            statement.setString(3, ownerId != null ? ownerId.toString() : null);
            statement.setString(4, petId != null ? petId.toString() : null);
            statement.setString(5, payload);
            statement.setLong(6, System.currentTimeMillis());
            statement.executeUpdate();
        } catch (SQLException error) {
            throw new PetPersistenceException("Network olayı yayımlanamadı.", error);
        }
    }

    public List<PetNetworkEvent> pollAfter(long cursor, int limit) {
        DatabaseThreadGuard.requireDatabaseThread();
        List<PetNetworkEvent> events = new ArrayList<>();
        String sql = "SELECT event_id, server_id, event_type, owner_id, pet_id, payload, created_at " +
                "FROM pet_network_events WHERE event_id > ? ORDER BY event_id ASC LIMIT ?";
        try (PreparedStatement statement = provider.getConnection().prepareStatement(sql)) {
            statement.setLong(1, cursor);
            statement.setInt(2, Math.max(1, Math.min(1000, limit)));
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    events.add(new PetNetworkEvent(result.getLong("event_id"), result.getString("server_id"),
                            PetNetworkEventType.valueOf(result.getString("event_type")), uuid(result.getString("owner_id")),
                            uuid(result.getString("pet_id")), result.getString("payload"), result.getLong("created_at")));
                }
            }
            return events;
        } catch (SQLException | IllegalArgumentException error) {
            throw new PetPersistenceException("Network olayları okunamadı.", error);
        }
    }

    public long latestId() {
        DatabaseThreadGuard.requireDatabaseThread();
        try (PreparedStatement statement = provider.getConnection().prepareStatement(
                "SELECT COALESCE(MAX(event_id), 0) FROM pet_network_events");
             ResultSet result = statement.executeQuery()) {
            return result.next() ? result.getLong(1) : 0L;
        } catch (SQLException error) {
            throw new PetPersistenceException("Network event imleci okunamadı.", error);
        }
    }

    public int deleteOlderThan(long timestamp) {
        DatabaseThreadGuard.requireDatabaseThread();
        try (PreparedStatement statement = provider.getConnection().prepareStatement(
                "DELETE FROM pet_network_events WHERE created_at < ?")) {
            statement.setLong(1, timestamp);
            return statement.executeUpdate();
        } catch (SQLException error) {
            throw new PetPersistenceException("Eski network olayları temizlenemedi.", error);
        }
    }

    private static UUID uuid(String value) {
        return value == null || value.isBlank() ? null : UUID.fromString(value);
    }
}

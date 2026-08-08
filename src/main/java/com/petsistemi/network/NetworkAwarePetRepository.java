package com.petsistemi.network;

import com.petsistemi.domain.PetInstance;
import com.petsistemi.persistence.PetRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class NetworkAwarePetRepository implements PetRepository {
    private final PetRepository delegate;
    private final JdbcPetNetworkEventStore events;
    private final String serverId;
    private final MysqlNetworkLockManager locks;

    public NetworkAwarePetRepository(PetRepository delegate, JdbcPetNetworkEventStore events, String serverId) {
        this(delegate, events, serverId, null);
    }

    public NetworkAwarePetRepository(PetRepository delegate, JdbcPetNetworkEventStore events, String serverId,
                                     MysqlNetworkLockManager locks) {
        this.delegate = delegate;
        this.events = events;
        this.serverId = serverId;
        this.locks = locks;
    }

    @Override public Optional<PetInstance> findById(UUID petId) { return delegate.findById(petId); }
    @Override public List<PetInstance> findByOwner(UUID ownerId) { return delegate.findByOwner(ownerId); }
    @Override public Optional<PetInstance> findActiveByOwner(UUID ownerId) { return delegate.findActiveByOwner(ownerId); }
    @Override public void insert(PetInstance pet) { locked(ownerKey(pet.ownerId()), () -> { delegate.insert(pet); publish(PetNetworkEventType.PET_CREATED, pet); }); }
    @Override public void update(PetInstance pet) { locked(ownerKey(pet.ownerId()), () -> { delegate.update(pet); publish(PetNetworkEventType.PET_UPDATED, pet); }); }
    @Override public void delete(UUID petId) {
        PetInstance known = delegate.findById(petId).orElse(null);
        if (known == null) return;
        locked(ownerKey(known.ownerId()), () -> {
            PetInstance before = delegate.findById(petId).orElse(null);
            delegate.delete(petId);
            if (before != null) publish(PetNetworkEventType.PET_REMOVED, before);
        });
    }
    @Override public void setActivePet(UUID ownerId, UUID petId) { locked(ownerKey(ownerId), () -> { delegate.setActivePet(ownerId, petId); selection(ownerId, petId); }); }
    @Override public void clearActivePet(UUID ownerId) { locked(ownerKey(ownerId), () -> { delegate.clearActivePet(ownerId); cleared(ownerId); }); }
    @Override public void switchActivePet(UUID ownerId, UUID previousPetId, UUID newPetId) { locked(ownerKey(ownerId), () -> { delegate.setActivePet(ownerId, newPetId); selection(ownerId, newPetId); }); }
    @Override public void clearActivePetAndSetAvailable(UUID ownerId, UUID petId) { locked(ownerKey(ownerId), () -> { delegate.clearActivePet(ownerId); cleared(ownerId); }); }
    @Override public void restoreActivePet(UUID ownerId, UUID previousPetId, UUID failedPetId) { locked(ownerKey(ownerId), () -> { if (previousPetId == null) delegate.clearActivePet(ownerId); else delegate.setActivePet(ownerId, previousPetId); selection(ownerId, previousPetId); }); }
    @Override public void disablePetTransactional(UUID ownerId, PetInstance pet) {
        if (locks == null) {
            delegate.disablePetTransactional(ownerId, pet);
            publish(PetNetworkEventType.PET_UPDATED, pet);
        } else {
            locked(ownerKey(ownerId), () -> { deleteSelection(ownerId, pet.petId()); delegate.update(pet); publish(PetNetworkEventType.PET_UPDATED, pet); });
        }
    }
    @Override public void removePetTransactional(UUID ownerId, UUID petId) {
        locked(ownerKey(ownerId), () -> {
            PetInstance before = delegate.findById(petId).orElse(null);
            delegate.delete(petId);
            if (before != null) publish(PetNetworkEventType.PET_REMOVED, before);
        });
    }

    private void publish(PetNetworkEventType type, PetInstance pet) { events.publish(serverId, type, pet.ownerId(), pet.petId(), pet.definitionId()); }
    private void selection(UUID ownerId, UUID petId) { events.publish(serverId, PetNetworkEventType.SELECTION_CHANGED, ownerId, petId, null); }
    private void cleared(UUID ownerId) { events.publish(serverId, PetNetworkEventType.SELECTION_CLEARED, ownerId, null, null); }
    private void locked(String key, Runnable action) { if (locks == null) action.run(); else locks.withLockTransaction(key, action); }
    private void deleteSelection(UUID ownerId, UUID petId) {
        try (java.sql.PreparedStatement statement = locks.connection().prepareStatement(
                "DELETE FROM player_selected_pets WHERE owner_id = ? AND pet_id = ?")) {
            statement.setString(1, ownerId.toString());
            statement.setString(2, petId.toString());
            statement.executeUpdate();
        } catch (java.sql.SQLException error) {
            throw new com.petsistemi.persistence.PetPersistenceException("Network pet seçimi temizlenemedi.", error);
        }
    }
    private static String ownerKey(UUID ownerId) { return "owner:" + ownerId; }
}

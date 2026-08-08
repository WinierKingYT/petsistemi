package com.petsistemi.network;

import com.petsistemi.domain.PetFollowMode;
import com.petsistemi.domain.PetSelection;
import com.petsistemi.persistence.PetSelectionRepository;

import java.util.Optional;
import java.util.UUID;

public final class NetworkAwarePetSelectionRepository implements PetSelectionRepository {
    private final PetSelectionRepository delegate;
    private final JdbcPetNetworkEventStore events;
    private final String serverId;
    private final MysqlNetworkLockManager locks;

    public NetworkAwarePetSelectionRepository(PetSelectionRepository delegate,
                                              JdbcPetNetworkEventStore events, String serverId) {
        this(delegate, events, serverId, null);
    }

    public NetworkAwarePetSelectionRepository(PetSelectionRepository delegate,
                                              JdbcPetNetworkEventStore events, String serverId,
                                              MysqlNetworkLockManager locks) {
        this.delegate = delegate;
        this.events = events;
        this.serverId = serverId;
        this.locks = locks;
    }

    @Override public Optional<PetSelection> findByOwner(UUID ownerId) { return delegate.findByOwner(ownerId); }
    @Override public void select(UUID ownerId, UUID petId) { locked(ownerId, () -> { delegate.select(ownerId, petId); changed(ownerId, petId, null); }); }
    @Override public void clear(UUID ownerId) { locked(ownerId, () -> { delegate.clear(ownerId); events.publish(serverId, PetNetworkEventType.SELECTION_CLEARED, ownerId, null, null); }); }
    @Override public void switchSelection(UUID ownerId, UUID previousPetId, UUID newPetId) { locked(ownerId, () -> { delegate.select(ownerId, newPetId); changed(ownerId, newPetId, null); }); }
    @Override public void updateFollowMode(UUID ownerId, PetFollowMode followMode) { locked(ownerId, () -> { delegate.updateFollowMode(ownerId, followMode); changed(ownerId, null, followMode != null ? followMode.name() : null); }); }

    private void changed(UUID ownerId, UUID petId, String payload) {
        events.publish(serverId, PetNetworkEventType.SELECTION_CHANGED, ownerId, petId, payload);
    }
    private void locked(UUID ownerId, Runnable action) {
        if (locks == null) action.run(); else locks.withLockTransaction("owner:" + ownerId, action);
    }
}

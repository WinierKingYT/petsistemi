package com.petsistemi.runtime;

import java.util.*;

public class ActivePetRegistry {

    private final Map<UUID, ActivePet> activePetsByOwner = new HashMap<>();
    private final Map<UUID, ActivePet> activePetsByEntity = new HashMap<>();

    public synchronized void register(ActivePet activePet) {
        activePetsByOwner.put(activePet.getOwnerId(), activePet);
        if (activePet.getEntityId() != null) {
            activePetsByEntity.put(activePet.getEntityId(), activePet);
        }
    }

    public synchronized void unregister(UUID ownerId) {
        ActivePet removed = activePetsByOwner.remove(ownerId);
        if (removed != null && removed.getEntityId() != null) {
            activePetsByEntity.remove(removed.getEntityId());
        }
    }

    public synchronized Optional<ActivePet> getByOwner(UUID ownerId) {
        return Optional.ofNullable(activePetsByOwner.get(ownerId));
    }

    public synchronized Optional<ActivePet> getByEntity(UUID entityId) {
        return Optional.ofNullable(activePetsByEntity.get(entityId));
    }

    public synchronized Collection<ActivePet> getAllActive() {
        return Collections.unmodifiableCollection(activePetsByOwner.values());
    }

    public synchronized void clear() {
        activePetsByOwner.clear();
        activePetsByEntity.clear();
    }
}

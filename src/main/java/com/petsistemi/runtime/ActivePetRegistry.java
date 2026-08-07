package com.petsistemi.runtime;

import org.bukkit.entity.Entity;

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

    /** Resolves a pet by its primary entity OR any tracked child entity (e.g. MULTI_ENTITY swarms). */
    public synchronized Optional<ActivePet> getByAnyEntity(UUID entityId) {
        Optional<ActivePet> direct = getByEntity(entityId);
        if (direct.isPresent()) {
            return direct;
        }
        for (ActivePet pet : activePetsByOwner.values()) {
            for (Entity child : pet.getChildren()) {
                if (child != null && child.getUniqueId().equals(entityId)) {
                    return Optional.of(pet);
                }
            }
        }
        return Optional.empty();
    }

    /** Resolves a pet by its persistent pet id (e.g. after an interaction hitbox lookup). */
    public synchronized Optional<ActivePet> getByPetId(UUID petId) {
        if (petId == null) {
            return Optional.empty();
        }
        for (ActivePet pet : activePetsByOwner.values()) {
            if (petId.equals(pet.getPetId())) {
                return Optional.of(pet);
            }
        }
        return Optional.empty();
    }

    public synchronized Collection<ActivePet> getAllActive() {
        return List.copyOf(activePetsByOwner.values());
    }

    public synchronized void clear() {
        activePetsByOwner.clear();
        activePetsByEntity.clear();
    }
}

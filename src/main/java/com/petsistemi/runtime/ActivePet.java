package com.petsistemi.runtime;

import com.petsistemi.domain.PetRuntimeState;
import org.bukkit.entity.Entity;
import java.util.UUID;

public final class ActivePet {

    private final UUID petId;
    private final UUID ownerId;
    private UUID entityId;
    private Entity spawnedEntity;
    private PetRuntimeState runtimeState;

    public ActivePet(UUID petId, UUID ownerId, UUID entityId, Entity spawnedEntity, PetRuntimeState runtimeState) {
        this.petId = petId;
        this.ownerId = ownerId;
        this.entityId = entityId;
        this.spawnedEntity = spawnedEntity;
        this.runtimeState = runtimeState;
    }

    public UUID getPetId() {
        return petId;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public UUID getEntityId() {
        return entityId;
    }

    public void setEntityId(UUID entityId) {
        this.entityId = entityId;
    }

    public Entity getSpawnedEntity() {
        return spawnedEntity;
    }

    public void setSpawnedEntity(Entity spawnedEntity) {
        this.spawnedEntity = spawnedEntity;
    }

    public PetRuntimeState getRuntimeState() {
        return runtimeState;
    }

    public void setRuntimeState(PetRuntimeState runtimeState) {
        this.runtimeState = runtimeState;
    }
}

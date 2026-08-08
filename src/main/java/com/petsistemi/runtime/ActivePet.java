package com.petsistemi.runtime;

import com.petsistemi.domain.PetFollowMode;
import com.petsistemi.domain.PetInstance;
import com.petsistemi.domain.PetMovementDefinition;
import com.petsistemi.domain.PetMovementType;
import com.petsistemi.domain.PetRuntimeState;
import com.petsistemi.domain.RuntimeRepresentationType;
import org.bukkit.entity.Entity;
import org.bukkit.NamespacedKey;
import com.petsistemi.domain.RuntimeKeyResolver;
import com.petsistemi.domain.animation.PetAnimationClipDefinition;
import com.petsistemi.domain.animation.PetAnimationState;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class ActivePet implements PetRuntimeHandle {

    private final UUID petId;
    private final UUID ownerId;
    private UUID entityId;
    private Entity spawnedEntity;
    private PetRuntimeState runtimeState;
    private PetFollowMode followMode = PetFollowMode.FOLLOW;
    private org.bukkit.Location stayLocation;

    private final String definitionId;
    private int level;

    /** Snapshot of the persisted pet state at spawn/refresh time; used for visual updates. */
    private PetInstance petInstance;

    private RuntimeRepresentationType representationType = RuntimeRepresentationType.ENTITY;
    private PetMovementType movementType = PetMovementType.GROUND_FOLLOW;
    private NamespacedKey representationKey = RuntimeKeyResolver.representationKey(RuntimeRepresentationType.ENTITY);
    private NamespacedKey movementKey = RuntimeKeyResolver.movementKey(PetMovementType.GROUND_FOLLOW);
    private PetMovementDefinition movementDefinition;
    private int updateIntervalTicks = 0;
    private int tickAccumulator = 0;

    private final java.util.List<Entity> children = new java.util.ArrayList<>();

    private boolean resting;
    private PetAnimationState animationState;
    private PetAnimationClipDefinition animationClip;

    public ActivePet(UUID petId, UUID ownerId, String definitionId, int level, UUID entityId, Entity spawnedEntity, PetRuntimeState runtimeState) {
        this.petId = petId;
        this.ownerId = ownerId;
        this.definitionId = definitionId;
        this.level = level;
        this.entityId = entityId;
        this.spawnedEntity = spawnedEntity;
        this.runtimeState = runtimeState;
    }

    public ActivePet(UUID petId, UUID ownerId, UUID entityId, Entity spawnedEntity, PetRuntimeState runtimeState) {
        this(petId, ownerId, "", 1, entityId, spawnedEntity, runtimeState);
    }

    public String getDefinitionId() {
        return definitionId;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
        if (petInstance != null) {
            petInstance = petInstance.withLevelAndExperience(level, petInstance.experience());
        }
    }

    public PetInstance getPetInstance() {
        return petInstance;
    }

    public void setPetInstance(PetInstance petInstance) {
        this.petInstance = petInstance;
        if (petInstance != null) {
            this.level = petInstance.level();
        }
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

    public PetFollowMode getFollowMode() {
        return followMode;
    }

    public void setFollowMode(PetFollowMode followMode) {
        this.followMode = followMode != null ? followMode : PetFollowMode.FOLLOW;
    }

    public org.bukkit.Location getStayLocation() {
        return stayLocation;
    }

    public void setStayLocation(org.bukkit.Location stayLocation) {
        this.stayLocation = stayLocation;
    }

    /** True while the pet is in the idle/sleep visual state (driven by {@link PetIdleSleepController}). */
    public boolean isResting() {
        return resting;
    }

    public void setResting(boolean resting) {
        this.resting = resting;
    }

    public PetAnimationState getAnimationState() {
        return animationState;
    }

    public void setAnimationState(PetAnimationState animationState) {
        this.animationState = animationState;
    }

    public PetAnimationClipDefinition getAnimationClip() {
        return animationClip;
    }

    public void setAnimationClip(PetAnimationClipDefinition animationClip) {
        this.animationClip = animationClip;
    }

    public RuntimeRepresentationType getRepresentationType() {
        return representationType;
    }

    public void setRepresentationType(RuntimeRepresentationType representationType) {
        this.representationType = representationType != null ? representationType : RuntimeRepresentationType.ENTITY;
        this.representationKey = RuntimeKeyResolver.representationKey(this.representationType);
    }
    public NamespacedKey getRepresentationKey() { return representationKey; }
    public void setRepresentationKey(NamespacedKey key) { this.representationKey = key != null ? key : RuntimeKeyResolver.representationKey(representationType); }

    public PetMovementType getMovementType() {
        return movementType;
    }

    public void setMovementType(PetMovementType movementType) {
        this.movementType = movementType != null ? movementType : PetMovementType.GROUND_FOLLOW;
        this.movementKey = RuntimeKeyResolver.movementKey(this.movementType);
    }
    public NamespacedKey getMovementKey() { return movementKey; }
    public void setMovementKey(NamespacedKey key) { this.movementKey = key != null ? key : RuntimeKeyResolver.movementKey(movementType); }

    public PetMovementDefinition getMovementDefinition() {
        return movementDefinition;
    }

    public void setMovementDefinition(PetMovementDefinition movementDefinition) {
        this.movementDefinition = movementDefinition;
    }

    /** 0 means "use the global tick interval". */
    public int getUpdateIntervalTicks() {
        return updateIntervalTicks;
    }

    public void setUpdateIntervalTicks(int updateIntervalTicks) {
        this.updateIntervalTicks = Math.max(0, updateIntervalTicks);
    }

    public int getTickAccumulator() {
        return tickAccumulator;
    }

    public void incrementTickAccumulator() {
        this.tickAccumulator++;
    }

    public void setTickAccumulator(int tickAccumulator) {
        this.tickAccumulator = Math.max(0, tickAccumulator);
    }

    public void addChild(Entity child) {
        if (child != null) {
            children.add(child);
        }
    }

    public void clearChildren() {
        children.clear();
    }

    public java.util.List<Entity> getChildren() {
        return java.util.Collections.unmodifiableList(children);
    }

    // ── PetRuntimeHandle ──

    @Override
    public UUID ownerId() {
        return ownerId;
    }

    @Override
    public UUID petId() {
        return petId;
    }

    @Override
    public RuntimeRepresentationType representationType() {
        return representationType;
    }

    @Override
    public Optional<Entity> primaryEntity() {
        return Optional.ofNullable(spawnedEntity);
    }

    @Override
    public Collection<Entity> entities() {
        if (spawnedEntity == null) {
            return List.of();
        }
        if (children.isEmpty()) {
            return List.of(spawnedEntity);
        }
        java.util.ArrayList<Entity> all = new java.util.ArrayList<>(children.size() + 1);
        all.add(spawnedEntity);
        all.addAll(children);
        return all;
    }

    @Override
    public boolean isValid() {
        return spawnedEntity != null && spawnedEntity.isValid() && !spawnedEntity.isDead();
    }

    @Override
    public void remove() {
        if (spawnedEntity != null && spawnedEntity.isValid()) {
            spawnedEntity.remove();
        }
    }
}

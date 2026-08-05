package com.petsistemi.api.event;

import com.petsistemi.api.PetSnapshot;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public class PetRenameEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID ownerId;
    private final Player player;
    private final PetSnapshot pet;
    private final String oldName;
    private String newName;
    private boolean cancelled = false;

    /**
     * Player-facing rename event. {@code ownerId} is non-null whenever a player or snapshot is available.
     *
     * @param player  the renaming player (nullable for system/admin paths)
     * @param ownerId the pet owner identity — never lost when derivable
     * @param pet     the pet snapshot
     * @param oldName the name before the rename
     * @param newName the (possibly event-modified) new name
     */
    public PetRenameEvent(@Nullable Player player, @Nullable UUID ownerId, @Nullable PetSnapshot pet, @Nullable String oldName, @NotNull String newName) {
        this.player = player;
        this.ownerId = ownerId;
        this.pet = pet;
        this.oldName = oldName;
        this.newName = Objects.requireNonNull(newName, "newName null olamaz.");
    }

    public PetRenameEvent(@Nullable Player player, @Nullable PetSnapshot pet, @Nullable String oldName, @NotNull String newName) {
        this(player, resolveOwnerId(player, pet), pet, oldName, newName);
    }

    public PetRenameEvent(@Nullable PetSnapshot pet, @Nullable String oldName, @NotNull String newName) {
        this(null, resolveOwnerId(null, pet), pet, oldName, newName);
    }

    public PetRenameEvent(@Nullable PetSnapshot pet, @NotNull String newName) {
        this(null, resolveOwnerId(null, pet), pet, pet != null ? pet.customName() : null, newName);
    }

    private static UUID resolveOwnerId(@Nullable Player player, @Nullable PetSnapshot pet) {
        if (player != null) return player.getUniqueId();
        if (pet != null && pet.ownerId() != null) return pet.ownerId();
        return null;
    }

    /** The pet owner identity. Null only when neither a player nor a snapshot is available. */
    @Nullable
    public UUID getOwnerId() {
        return ownerId;
    }

    @Nullable
    public Player getPlayer() {
        return player;
    }

    public Optional<Player> getOptionalPlayer() {
        return Optional.ofNullable(player);
    }

    public PetSnapshot getPet() {
        return pet;
    }

    public String getOldName() {
        return oldName;
    }

    public String getNewName() {
        return newName;
    }

    public void setNewName(String newName) {
        this.newName = Objects.requireNonNull(newName, "newName null olamaz.");
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}

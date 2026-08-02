package com.petsistemi.api.event;

import com.petsistemi.api.PetSnapshot;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class PetSelectionChangedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID ownerId;
    private final PetSnapshot previousSelection;
    private final PetSnapshot newSelection;

    public PetSelectionChangedEvent(@NotNull UUID ownerId, @Nullable PetSnapshot previousSelection, @Nullable PetSnapshot newSelection) {
        this.ownerId = ownerId;
        this.previousSelection = previousSelection;
        this.newSelection = newSelection;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public PetSnapshot getPreviousSelection() {
        return previousSelection;
    }

    public PetSnapshot getNewSelection() {
        return newSelection;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}

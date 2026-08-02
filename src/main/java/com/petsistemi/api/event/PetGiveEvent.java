package com.petsistemi.api.event;

import com.petsistemi.api.PetSnapshot;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class PetGiveEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID ownerId;
    private final PetSnapshot pet;

    public PetGiveEvent(UUID ownerId, PetSnapshot pet) {
        this.ownerId = ownerId;
        this.pet = pet;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public PetSnapshot getPet() {
        return pet;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}

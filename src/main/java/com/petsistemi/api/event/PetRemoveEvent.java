package com.petsistemi.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import java.util.UUID;

public class PetRemoveEvent extends Event {
    private static final HandlerList handlers = new HandlerList();
    private final UUID ownerId;
    private final UUID petId;

    public PetRemoveEvent(UUID ownerId, UUID petId) {
        this.ownerId = ownerId;
        this.petId = petId;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public UUID getPetId() {
        return petId;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}

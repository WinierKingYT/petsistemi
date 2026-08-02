package com.petsistemi.api.event;

import com.petsistemi.domain.PetInstance;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import java.util.UUID;

public class PetGiveEvent extends Event {
    private static final HandlerList handlers = new HandlerList();
    private final UUID ownerId;
    private final PetInstance petInstance;

    public PetGiveEvent(UUID ownerId, PetInstance petInstance) {
        this.ownerId = ownerId;
        this.petInstance = petInstance;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public PetInstance getPetInstance() {
        return petInstance;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}

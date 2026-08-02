package com.petsistemi.api.event;

import com.petsistemi.domain.PetInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class PetDismissEvent extends Event {
    private static final HandlerList handlers = new HandlerList();
    private final Player player;
    private final PetInstance petInstance;

    public PetDismissEvent(Player player, PetInstance petInstance) {
        this.player = player;
        this.petInstance = petInstance;
    }

    public Player getPlayer() {
        return player;
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

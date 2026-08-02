package com.petsistemi.api.event;

import com.petsistemi.domain.PetInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class PetPreSummonEvent extends Event implements Cancellable {
    private static final HandlerList handlers = new HandlerList();
    private final Player player;
    private final PetInstance petInstance;
    private boolean cancelled = false;

    public PetPreSummonEvent(Player player, PetInstance petInstance) {
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
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}

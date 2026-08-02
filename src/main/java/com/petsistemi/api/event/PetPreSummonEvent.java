package com.petsistemi.api.event;

import com.petsistemi.api.PetSnapshot;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class PetPreSummonEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final PetSnapshot pet;
    private boolean cancelled = false;

    public PetPreSummonEvent(Player player, PetSnapshot pet) {
        this.player = player;
        this.pet = pet;
    }

    public Player getPlayer() {
        return player;
    }

    public PetSnapshot getPet() {
        return pet;
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

package com.petsistemi.api.event;

import com.petsistemi.domain.PetInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class PetRenameEvent extends Event implements Cancellable {
    private static final HandlerList handlers = new HandlerList();
    private final Player player;
    private final PetInstance petInstance;
    private final String oldName;
    private String newName;
    private boolean cancelled = false;

    public PetRenameEvent(Player player, PetInstance petInstance, String oldName, String newName) {
        this.player = player;
        this.petInstance = petInstance;
        this.oldName = oldName;
        this.newName = newName;
    }

    public Player getPlayer() {
        return player;
    }

    public PetInstance getPetInstance() {
        return petInstance;
    }

    public String getOldName() {
        return oldName;
    }

    public String getNewName() {
        return newName;
    }

    public void setNewName(String newName) {
        this.newName = newName;
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

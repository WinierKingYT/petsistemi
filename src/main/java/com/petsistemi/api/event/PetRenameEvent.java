package com.petsistemi.api.event;

import com.petsistemi.api.PetSnapshot;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class PetRenameEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final PetSnapshot pet;
    private final String oldName;
    private String newName;
    private boolean cancelled = false;

    public PetRenameEvent(Player player, PetSnapshot pet, String oldName, String newName) {
        this.player = player;
        this.pet = pet;
        this.oldName = oldName;
        this.newName = newName;
    }

    public Player getPlayer() {
        return player;
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
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}

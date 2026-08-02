package com.petsistemi.api.event;

import com.petsistemi.api.PetSnapshot;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class PetSummonEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final PetSnapshot pet;
    private final Entity entity;

    public PetSummonEvent(Player player, PetSnapshot pet, Entity entity) {
        this.player = player;
        this.pet = pet;
        this.entity = entity;
    }

    public Player getPlayer() {
        return player;
    }

    public PetSnapshot getPet() {
        return pet;
    }

    public Entity getEntity() {
        return entity;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}

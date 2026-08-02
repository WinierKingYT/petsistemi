package com.petsistemi.api.event;

import com.petsistemi.domain.PetInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class PetSummonEvent extends Event {
    private static final HandlerList handlers = new HandlerList();
    private final Player player;
    private final PetInstance petInstance;
    private final Entity spawnedEntity;

    public PetSummonEvent(Player player, PetInstance petInstance, Entity spawnedEntity) {
        this.player = player;
        this.petInstance = petInstance;
        this.spawnedEntity = spawnedEntity;
    }

    public Player getPlayer() {
        return player;
    }

    public PetInstance getPetInstance() {
        return petInstance;
    }

    public Entity getSpawnedEntity() {
        return spawnedEntity;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}

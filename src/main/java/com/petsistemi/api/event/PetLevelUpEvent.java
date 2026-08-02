package com.petsistemi.api.event;

import com.petsistemi.domain.PetInstance;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class PetLevelUpEvent extends Event {
    private static final HandlerList handlers = new HandlerList();
    private final PetInstance petInstance;
    private final int oldLevel;
    private final int newLevel;

    public PetLevelUpEvent(PetInstance petInstance, int oldLevel, int newLevel) {
        this.petInstance = petInstance;
        this.oldLevel = oldLevel;
        this.newLevel = newLevel;
    }

    public PetInstance getPetInstance() {
        return petInstance;
    }

    public int getOldLevel() {
        return oldLevel;
    }

    public int getNewLevel() {
        return newLevel;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}

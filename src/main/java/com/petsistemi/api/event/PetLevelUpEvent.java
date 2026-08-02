package com.petsistemi.api.event;

import com.petsistemi.api.PetSnapshot;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class PetLevelUpEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final PetSnapshot pet;
    private final int oldLevel;
    private final int newLevel;

    public PetLevelUpEvent(PetSnapshot pet, int oldLevel, int newLevel) {
        this.pet = pet;
        this.oldLevel = oldLevel;
        this.newLevel = newLevel;
    }

    public PetSnapshot getPet() {
        return pet;
    }

    public int getOldLevel() {
        return oldLevel;
    }

    public int getNewLevel() {
        return newLevel;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}

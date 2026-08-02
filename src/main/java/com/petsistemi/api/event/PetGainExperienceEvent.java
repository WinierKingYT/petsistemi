package com.petsistemi.api.event;

import com.petsistemi.api.PetSnapshot;
import com.petsistemi.domain.ExperienceSource;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class PetGainExperienceEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final PetSnapshot pet;
    private long amount;
    private final ExperienceSource source;
    private boolean cancelled = false;

    public PetGainExperienceEvent(PetSnapshot pet, long amount, ExperienceSource source) {
        this.pet = pet;
        this.amount = amount;
        this.source = source;
    }

    public PetSnapshot getPet() {
        return pet;
    }

    public long getAmount() {
        return amount;
    }

    public void setAmount(long amount) {
        this.amount = amount;
    }

    public ExperienceSource getSource() {
        return source;
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

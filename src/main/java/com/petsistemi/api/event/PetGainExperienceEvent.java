package com.petsistemi.api.event;

import com.petsistemi.domain.ExperienceSource;
import com.petsistemi.domain.PetInstance;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class PetGainExperienceEvent extends Event implements Cancellable {
    private static final HandlerList handlers = new HandlerList();
    private final PetInstance petInstance;
    private long amount;
    private final ExperienceSource source;
    private boolean cancelled = false;

    public PetGainExperienceEvent(PetInstance petInstance, long amount, ExperienceSource source) {
        this.petInstance = petInstance;
        this.amount = amount;
        this.source = source;
    }

    public PetInstance getPetInstance() {
        return petInstance;
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
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}

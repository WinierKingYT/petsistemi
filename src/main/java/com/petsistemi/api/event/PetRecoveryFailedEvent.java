package com.petsistemi.api.event;

import com.petsistemi.runtime.RecoveryOutcome;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class PetRecoveryFailedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID ownerId;
    private final UUID petId;
    private final RecoveryOutcome outcome;

    public PetRecoveryFailedEvent(@NotNull UUID ownerId, @NotNull UUID petId, @NotNull RecoveryOutcome outcome) {
        this.ownerId = ownerId;
        this.petId = petId;
        this.outcome = outcome;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public UUID getPetId() {
        return petId;
    }

    public RecoveryOutcome getOutcome() {
        return outcome;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}

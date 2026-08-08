package com.petsistemi.api.event;

import com.petsistemi.api.PetSnapshot;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class PetEvolutionEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final PetSnapshot before;
    private final PetSnapshot after;
    private final Entity entity;

    public PetEvolutionEvent(Player player, PetSnapshot before, PetSnapshot after, Entity entity) {
        this.player = player;
        this.before = before;
        this.after = after;
        this.entity = entity;
    }

    public Player getPlayer() { return player; }
    public PetSnapshot getBefore() { return before; }
    public PetSnapshot getAfter() { return after; }
    public Entity getEntity() { return entity; }
    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}

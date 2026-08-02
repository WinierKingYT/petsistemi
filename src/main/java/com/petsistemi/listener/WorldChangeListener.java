package com.petsistemi.listener;

import com.petsistemi.runtime.ActivePet;
import com.petsistemi.runtime.ActivePetRegistry;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.Optional;

public class WorldChangeListener implements Listener {

    private final ActivePetRegistry activeRegistry;

    public WorldChangeListener(ActivePetRegistry activeRegistry) {
        this.activeRegistry = activeRegistry;
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        Optional<ActivePet> activeOpt = activeRegistry.getByOwner(player.getUniqueId());
        activeOpt.ifPresent(active -> {
            if (active.getSpawnedEntity() != null && active.getSpawnedEntity().isValid()) {
                active.getSpawnedEntity().teleport(player.getLocation());
            }
        });
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        Optional<ActivePet> activeOpt = activeRegistry.getByOwner(player.getUniqueId());
        activeOpt.ifPresent(active -> {
            if (active.getSpawnedEntity() != null && active.getSpawnedEntity().isValid()) {
                active.getSpawnedEntity().teleport(event.getTo());
            }
        });
    }
}

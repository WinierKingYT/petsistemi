package com.petsistemi.listener;

import com.petsistemi.runtime.ActivePet;
import com.petsistemi.runtime.ActivePetRegistry;
import com.petsistemi.runtime.SafePetLocationFinder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Optional;

public class WorldChangeListener implements Listener {

    private final JavaPlugin plugin;
    private final ActivePetRegistry activeRegistry;

    public WorldChangeListener(JavaPlugin plugin, ActivePetRegistry activeRegistry) {
        this.plugin = plugin;
        this.activeRegistry = activeRegistry;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        schedulePetTeleport(player, player.getLocation());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (event.getTo() == null) return;
        Player player = event.getPlayer();
        schedulePetTeleport(player, event.getTo());
    }

    private void schedulePetTeleport(Player player, Location targetLocation) {
        // Execute on next tick after player teleport completes successfully
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;

            Optional<ActivePet> activeOpt = activeRegistry.getByOwner(player.getUniqueId());
            activeOpt.ifPresent(active -> {
                if (active.getSpawnedEntity() != null && active.getSpawnedEntity().isValid()) {
                    Location safeLoc = SafePetLocationFinder.findSafeLocation(targetLocation);
                    active.getSpawnedEntity().teleport(safeLoc);
                }
            });
        });
    }
}

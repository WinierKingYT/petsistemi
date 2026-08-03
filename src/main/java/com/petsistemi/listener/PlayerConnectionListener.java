package com.petsistemi.listener;

import com.petsistemi.api.PetService;
import com.petsistemi.api.PetSnapshot;
import com.petsistemi.persistence.DatabaseExecutor;
import com.petsistemi.persistence.PlayerPetProfileCache;
import com.petsistemi.runtime.PetRuntimeCoordinator;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Optional;

public class PlayerConnectionListener implements Listener {

    private final JavaPlugin plugin;
    private final PetService petService;
    private final PetRuntimeCoordinator coordinator;
    private final PlayerPetProfileCache profileCache;
    private final DatabaseExecutor dbExecutor;

    public PlayerConnectionListener(JavaPlugin plugin, PetService petService, PetRuntimeCoordinator coordinator, PlayerPetProfileCache profileCache, DatabaseExecutor dbExecutor) {
        this.plugin = plugin;
        this.petService = petService;
        this.coordinator = coordinator;
        this.profileCache = profileCache;
        this.dbExecutor = dbExecutor;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // 1. Asynchronously load profile into cache
        if (dbExecutor != null && profileCache != null) {
            dbExecutor.executeAsync(() -> profileCache.loadProfile(player.getUniqueId()));
        }

        // 2. Restore pet 20 ticks later after chunk loading
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                coordinator.restoreOnJoin(player);
            }
        }, 20L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        coordinator.despawnOnQuit(event.getPlayer().getUniqueId());
        if (profileCache != null) {
            profileCache.invalidate(event.getPlayer().getUniqueId());
        }
    }
}

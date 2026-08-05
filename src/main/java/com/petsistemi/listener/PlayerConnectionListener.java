package com.petsistemi.listener;

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

import java.util.UUID;
import java.util.logging.Level;

public class PlayerConnectionListener implements Listener {

    private final JavaPlugin plugin;
    private final PetRuntimeCoordinator coordinator;
    private final PlayerPetProfileCache profileCache;
    private final DatabaseExecutor dbExecutor;

    public PlayerConnectionListener(JavaPlugin plugin, PetRuntimeCoordinator coordinator, PlayerPetProfileCache profileCache, DatabaseExecutor dbExecutor) {
        this.plugin = plugin;
        this.coordinator = coordinator;
        this.profileCache = profileCache;
        this.dbExecutor = dbExecutor;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID ownerId = player.getUniqueId();

        if (profileCache != null) {
            profileCache.loadProfileAsync(dbExecutor, ownerId).whenComplete((profile, ex) -> {
                if (ex != null) {
                    if (plugin != null) {
                        plugin.getLogger().log(Level.SEVERE, "Profil yükleme hatası [" + ownerId + "]: " + ex.getMessage(), ex);
                    }
                    return;
                }
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (player.isOnline()) {
                        coordinator.restoreOnJoin(player);
                    }
                });
            });
        } else {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) {
                    coordinator.restoreOnJoin(player);
                }
            });
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID ownerId = event.getPlayer().getUniqueId();
        coordinator.despawnOnQuit(ownerId);
        if (profileCache != null) {
            profileCache.invalidate(ownerId);
        }
    }
}

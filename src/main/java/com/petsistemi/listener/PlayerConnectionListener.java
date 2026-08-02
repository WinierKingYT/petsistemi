package com.petsistemi.listener;

import com.petsistemi.api.PetService;
import com.petsistemi.api.PetSnapshot;
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

    public PlayerConnectionListener(JavaPlugin plugin, PetService petService, PetRuntimeCoordinator coordinator) {
        this.plugin = plugin;
        this.petService = petService;
        this.coordinator = coordinator;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        
        // Wait 20 ticks (1s) to allow player chunk loading before summoning pet
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                Optional<PetSnapshot> activeOpt = petService.getActivePet(player.getUniqueId());
                activeOpt.ifPresent(snapshot -> petService.summon(player, snapshot.petId()));
            }
        }, 20L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // Despawn physical entity & active runtime registry, preserving selected pet in DB
        coordinator.despawnOnQuit(event.getPlayer().getUniqueId());
    }
}

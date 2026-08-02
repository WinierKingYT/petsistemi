package com.petsistemi.listener;

import com.petsistemi.api.PetService;
import com.petsistemi.api.PetSnapshot;
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

    public PlayerConnectionListener(JavaPlugin plugin, PetService petService) {
        this.plugin = plugin;
        this.petService = petService;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        
        // Wait 20 ticks (1s) to allow player to fully load into the chunk safely
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                Optional<PetSnapshot> activeOpt = petService.getActivePet(player.getUniqueId());
                activeOpt.ifPresent(snapshot -> petService.summon(player, snapshot.petId()));
            }
        }, 20L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        petService.dismiss(event.getPlayer());
    }
}

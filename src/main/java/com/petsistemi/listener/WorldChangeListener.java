package com.petsistemi.listener;

import com.petsistemi.application.PetRuntimeOperationService;
import com.petsistemi.runtime.ActivePet;
import com.petsistemi.runtime.ActivePetRegistry;
import com.petsistemi.runtime.PetRuntimeCoordinator;
import com.petsistemi.runtime.SafePetLocationFinder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
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
    private final PetRuntimeCoordinator coordinator;
    private final PetRuntimeOperationService operationService;

    public WorldChangeListener(JavaPlugin plugin, ActivePetRegistry activeRegistry, PetRuntimeCoordinator coordinator, PetRuntimeOperationService operationService) {
        this.plugin = plugin;
        this.activeRegistry = activeRegistry;
        this.coordinator = coordinator;
        this.operationService = operationService;
    }

    public WorldChangeListener(JavaPlugin plugin, ActivePetRegistry activeRegistry, PetRuntimeCoordinator coordinator) {
        this(plugin, activeRegistry, coordinator, null);
    }

    public WorldChangeListener(JavaPlugin plugin, ActivePetRegistry activeRegistry) {
        this(plugin, activeRegistry, null, null);
    }

    private boolean isWorldDisabled(String worldName) {
        if (worldName == null) return false;
        String lower = worldName.toLowerCase(java.util.Locale.ROOT);
        return lower.contains("bedwars") || lower.contains("minigames");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;

            if (isWorldDisabled(player.getWorld().getName())) {
                if (coordinator != null) {
                    coordinator.despawnRuntime(player.getUniqueId());
                }
                return;
            }

            Optional<ActivePet> activeOpt = activeRegistry.getByOwner(player.getUniqueId());
            if (activeOpt.isEmpty()) return;

            if (coordinator != null) {
                coordinator.despawnRuntime(player.getUniqueId());
            }

            if (operationService != null) {
                operationService.restoreSelectedPetAsync(player);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Location to = event.getTo();
        if (to == null) return;

        if (event.getFrom().getWorld() != null
                && to.getWorld() != null
                && !event.getFrom().getWorld().equals(to.getWorld())) {
            Player player = event.getPlayer();
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline()) return;
                if (coordinator != null) {
                    coordinator.despawnRuntime(player.getUniqueId());
                }
                if (operationService != null) {
                    operationService.restoreSelectedPetAsync(player);
                }
            }, 2L);
            return;
        }

        Player player = event.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            activeRegistry.getByOwner(player.getUniqueId()).ifPresent(active -> {
                Entity entity = active.getSpawnedEntity();
                if (entity != null && entity.isValid() && !entity.isDead()) {
                    entity.teleport(SafePetLocationFinder.findSafeLocation(player.getLocation()));
                }
            });
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(org.bukkit.event.player.PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            activeRegistry.getByOwner(player.getUniqueId()).ifPresent(active -> {
                Entity entity = active.getSpawnedEntity();
                if (entity != null && entity.isValid() && !entity.isDead()) {
                    entity.teleport(SafePetLocationFinder.findSafeLocation(event.getRespawnLocation()));
                } else if (operationService != null) {
                    operationService.restoreSelectedPetAsync(player);
                }
            });
        }, 5L);
    }
}

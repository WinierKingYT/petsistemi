package com.petsistemi.listener;

import com.petsistemi.application.PetRuntimeOperationService;
import com.petsistemi.bootstrap.MainThreadDispatcher;
import com.petsistemi.persistence.DatabaseExecutor;
import com.petsistemi.persistence.PlayerPetProfileCache;
import com.petsistemi.runtime.PetRuntimeCoordinator;
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
    private final PetRuntimeOperationService operationService;
    private final PlayerPetProfileCache profileCache;
    private final DatabaseExecutor dbExecutor;
    private final MainThreadDispatcher mainThreadDispatcher;

    public PlayerConnectionListener(JavaPlugin plugin,
                                    PetRuntimeCoordinator coordinator,
                                    PetRuntimeOperationService operationService,
                                    PlayerPetProfileCache profileCache,
                                    DatabaseExecutor dbExecutor,
                                    MainThreadDispatcher mainThreadDispatcher) {
        this.plugin = plugin;
        this.coordinator = coordinator;
        this.operationService = operationService;
        this.profileCache = profileCache;
        this.dbExecutor = dbExecutor;
        this.mainThreadDispatcher = mainThreadDispatcher;
    }

    public PlayerConnectionListener(JavaPlugin plugin, PetRuntimeCoordinator coordinator, PlayerPetProfileCache profileCache, DatabaseExecutor dbExecutor) {
        this(plugin, coordinator, null, profileCache, dbExecutor, null);
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

                Runnable restoreTask = () -> {
                    if (player.isOnline() && profileCache.getProfile(ownerId).isPresent()) {
                        if (operationService != null) {
                            operationService.restoreSelectedPetAsync(player);
                        }
                    }
                };

                if (mainThreadDispatcher != null) {
                    mainThreadDispatcher.run(restoreTask);
                } else if (plugin != null) {
                    org.bukkit.Bukkit.getScheduler().runTask(plugin, restoreTask);
                }
            });
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID ownerId = event.getPlayer().getUniqueId();
        if (profileCache != null) {
            profileCache.invalidate(ownerId);
        }
        if (coordinator != null) {
            coordinator.despawnRuntime(ownerId);
        }
    }
}

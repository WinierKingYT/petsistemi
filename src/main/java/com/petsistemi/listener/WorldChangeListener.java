package com.petsistemi.listener;

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

    public WorldChangeListener(JavaPlugin plugin, ActivePetRegistry activeRegistry, PetRuntimeCoordinator coordinator) {
        this.plugin        = plugin;
        this.activeRegistry = activeRegistry;
        this.coordinator   = coordinator;
    }

    /** Backward-compatible constructor for registrars that don't pass coordinator yet. */
    public WorldChangeListener(JavaPlugin plugin, ActivePetRegistry activeRegistry) {
        this(plugin, activeRegistry, null);
    }

    /**
     * Handles cross-world travel. The pet entity is invalid in the new world so we
     * despawn the old entity and restore via coordinator (which respawns in new world).
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            Optional<ActivePet> activeOpt = activeRegistry.getByOwner(player.getUniqueId());
            if (activeOpt.isEmpty()) return;

            ActivePet active = activeOpt.get();
            Entity entity = active.getSpawnedEntity();

            // Entity from previous world is now invalid — remove it
            if (entity != null && entity.isValid()) entity.remove();

            // Restore pet in new world via coordinator if available
            if (coordinator != null) {
                coordinator.restoreOnJoin(player);
            } else {
                // Fallback: teleport (only works intra-world, but safe guard)
                if (entity != null && !entity.isDead()) {
                    entity.teleport(SafePetLocationFinder.findSafeLocation(player.getLocation()));
                }
            }
        });
    }

    /**
     * Handles intra-world teleports (portals, /tp, etc.).
     * The entity is still valid — just move it to the target location.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Location to = event.getTo();
        if (to == null) return;

        // Cross-world teleports are handled by onWorldChange
        if (event.getFrom().getWorld() != null
                && to.getWorld() != null
                && !event.getFrom().getWorld().equals(to.getWorld())) {
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
}

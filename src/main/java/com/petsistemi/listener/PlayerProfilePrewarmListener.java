package com.petsistemi.listener;

import com.petsistemi.api.AsyncPetService;
import com.petsistemi.api.PetService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Objects;
import java.util.UUID;

/**
 * Pre-warms player pet profiles on login so GUI interactions open instantly (0-ms latency).
 */
public class PlayerProfilePrewarmListener implements Listener {

    private final PetService petService;

    public PlayerProfilePrewarmListener(PetService petService) {
        this.petService = Objects.requireNonNull(petService, "petService null olamaz.");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAsyncPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return;
        }

        UUID uuid = event.getUniqueId();
        if (petService instanceof AsyncPetService asyncPetService) {
            // Asynchronously fetch and prime profile cache prior to join completion
            asyncPetService.getOwnedPetsAsync(uuid);
        } else {
            petService.getOwnedPets(uuid);
        }
    }

    public void prewarmAllOnlinePlayers() {
        if (org.bukkit.Bukkit.getServer() == null) return;
        for (org.bukkit.entity.Player player : org.bukkit.Bukkit.getOnlinePlayers()) {
            if (petService instanceof AsyncPetService asyncPetService) {
                asyncPetService.getOwnedPetsAsync(player.getUniqueId());
            } else {
                petService.getOwnedPets(player.getUniqueId());
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Active pet despawn on quit handled by PetRuntimeCoordinator
    }
}

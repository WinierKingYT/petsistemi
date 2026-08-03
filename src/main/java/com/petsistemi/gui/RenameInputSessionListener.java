package com.petsistemi.gui;

import com.petsistemi.api.PetService;
import com.petsistemi.api.result.PetRenameResult;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

public class RenameInputSessionListener implements Listener {

    private final JavaPlugin plugin;
    private final PetService petService;
    private final PlayerInputSessionManager sessionManager;

    public RenameInputSessionListener(JavaPlugin plugin, PetService petService, PlayerInputSessionManager sessionManager) {
        this.plugin = plugin;
        this.petService = petService;
        this.sessionManager = sessionManager;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (!sessionManager.hasActiveSession(player.getUniqueId())) {
            return;
        }

        event.setCancelled(true);
        UUID petId = sessionManager.getTargetPetId(player.getUniqueId());
        sessionManager.removeSession(player.getUniqueId());

        String input = event.getMessage().trim();
        if ("iptal".equalsIgnoreCase(input) || "cancel".equalsIgnoreCase(input)) {
            player.sendMessage(Component.text("İsim değiştirme işlemi iptal edildi.", NamedTextColor.YELLOW));
            plugin.getServer().getScheduler().runTask(plugin, () -> PetListMenu.open(player, petService, 0, plugin));
            return;
        }

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            PetRenameResult result = petService.rename(player, petId, input);
            if (result.success()) {
                player.sendMessage(Component.text("Pet ismi başarıyla değiştirildi: " + input, NamedTextColor.GREEN));
            } else {
                player.sendMessage(Component.text("İsim değiştirilemedi: " + result.message(), NamedTextColor.RED));
            }
            PetListMenu.open(player, petService, 0, plugin);
        });
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        sessionManager.removeSession(event.getPlayer().getUniqueId());
    }
}

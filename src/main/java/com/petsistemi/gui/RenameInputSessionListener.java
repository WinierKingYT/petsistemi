package com.petsistemi.gui;

import com.petsistemi.api.AsyncPetService;
import com.petsistemi.api.PetService;
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
import java.util.concurrent.CompletableFuture;

public class RenameInputSessionListener implements Listener {

    private final JavaPlugin plugin;
    private final PetService petService;
    private final PlayerInputSessionManager sessionManager;
    private final com.petsistemi.definition.PetDefinitionRegistry definitionRegistry;

    public RenameInputSessionListener(JavaPlugin plugin, PetService petService, PlayerInputSessionManager sessionManager, com.petsistemi.definition.PetDefinitionRegistry definitionRegistry) {
        this.plugin = plugin;
        this.petService = petService;
        this.sessionManager = sessionManager;
        this.definitionRegistry = definitionRegistry;
    }

    public RenameInputSessionListener(JavaPlugin plugin, PetService petService, PlayerInputSessionManager sessionManager) {
        this(plugin, petService, sessionManager, null);
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
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) {
                    player.sendMessage(Component.text("İsim değiştirme işlemi iptal edildi.", NamedTextColor.YELLOW));
                    PetListMenu.open(player, petService, 0, plugin, definitionRegistry);
                }
            });
            return;
        }

        CompletableFuture<?> renameFuture = petService instanceof AsyncPetService async ? async.renameAsync(player.getUniqueId(), petId, input) : CompletableFuture.completedFuture(petService.rename(player.getUniqueId(), petId, input));

        renameFuture.whenComplete((res, ex) -> {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) {
                    boolean success = res instanceof com.petsistemi.api.result.PetRenameResult r && r.success();
                    String msg = res instanceof com.petsistemi.api.result.PetRenameResult r ? r.message() : (ex != null ? ex.getMessage() : "İşlem başarısız.");
                    if (success) {
                        player.sendMessage(Component.text("Pet ismi başarıyla değiştirildi: " + input, NamedTextColor.GREEN));
                    } else {
                        player.sendMessage(Component.text("İsim değiştirilemedi: " + msg, NamedTextColor.RED));
                    }
                    PetListMenu.open(player, petService, 0, plugin, definitionRegistry);
                }
            });
        });
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        sessionManager.removeSession(event.getPlayer().getUniqueId());
    }
}

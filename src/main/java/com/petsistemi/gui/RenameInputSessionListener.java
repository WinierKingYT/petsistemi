package com.petsistemi.gui;

import com.petsistemi.api.AsyncPetService;
import com.petsistemi.api.PetService;
import com.petsistemi.config.RuntimeConfigurationSnapshot;
import com.petsistemi.message.MessageService;
import com.petsistemi.message.PlaceholderMap;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

public class RenameInputSessionListener implements Listener {

    private final JavaPlugin plugin;
    private final PetService petService;
    private final PlayerInputSessionManager sessionManager;
    private final com.petsistemi.definition.PetDefinitionRegistry definitionRegistry;
    private final MessageService messageService;
    private final AtomicReference<RuntimeConfigurationSnapshot> configSnapshot;

    public RenameInputSessionListener(JavaPlugin plugin, PetService petService, PlayerInputSessionManager sessionManager, com.petsistemi.definition.PetDefinitionRegistry definitionRegistry, MessageService messageService, AtomicReference<RuntimeConfigurationSnapshot> configSnapshot) {
        this.plugin = plugin;
        this.petService = petService;
        this.sessionManager = sessionManager;
        this.definitionRegistry = definitionRegistry;
        this.messageService = messageService;
        this.configSnapshot = configSnapshot;
    }

    public RenameInputSessionListener(JavaPlugin plugin, PetService petService, PlayerInputSessionManager sessionManager, com.petsistemi.definition.PetDefinitionRegistry definitionRegistry) {
        this(plugin, petService, sessionManager, definitionRegistry, null, null);
    }

    public RenameInputSessionListener(JavaPlugin plugin, PetService petService, PlayerInputSessionManager sessionManager) {
        this(plugin, petService, sessionManager, null, null, null);
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
                    send(player, "command.rename-cancelled", "<red>İsim değiştirme işlemi iptal edildi.</red>");
                    PetListMenu.open(player, petService, 0, plugin, definitionRegistry, configSnapshot, messageService);
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
                        send(player, "command.pet-renamed", "<green>Pet ismi başarıyla değiştirildi: " + input + "</green>", PlaceholderMap.of("name", input));
                    } else {
                        send(player, "command.rename-failed", "<red>İsim değiştirilemedi: " + msg + "</red>", PlaceholderMap.of("error", msg));
                    }
                    PetListMenu.open(player, petService, 0, plugin, definitionRegistry, configSnapshot, messageService);
                }
            });
        });
    }

    private void send(Player player, String key, String fallback) {
        send(player, key, fallback, null);
    }

    private void send(Player player, String key, String fallback, PlaceholderMap placeholders) {
        if (messageService != null) {
            messageService.send(player, key, fallback, placeholders);
        } else if (player != null) {
            player.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(fallback));
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        sessionManager.removeSession(event.getPlayer().getUniqueId());
    }
}

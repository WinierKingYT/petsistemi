package com.petsistemi.gui;

import com.petsistemi.definition.editor.PetEditorField;
import com.petsistemi.definition.editor.PetEditorSessionManager;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

/** Captures one chat message for an editor field without touching Bukkit state asynchronously. */
public final class PetEditorInputListener implements Listener {

    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private final JavaPlugin plugin;
    private final PetEditorSessionManager sessions;

    public PetEditorInputListener(JavaPlugin plugin, PetEditorSessionManager sessions) {
        this.plugin = plugin;
        this.sessions = sessions;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        PetEditorField field = sessions.awaiting(player.getUniqueId()).orElse(null);
        if (field == null) return;
        event.setCancelled(true);
        String input = event.getMessage().trim();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            if ("iptal".equalsIgnoreCase(input) || "cancel".equalsIgnoreCase(input)) {
                sessions.await(player.getUniqueId(), null);
                player.sendMessage(MINI.deserialize("<yellow>Alan değişikliği iptal edildi.</yellow>"));
            } else {
                sessions.applyAwaited(player.getUniqueId(), input);
                player.sendMessage(MINI.deserialize("<green>" + field.label() + " taslakta güncellendi.</green>"));
            }
            PetDefinitionEditorMenu.openDefinition(player, plugin, sessions);
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        sessions.discard(event.getPlayer().getUniqueId());
    }
}

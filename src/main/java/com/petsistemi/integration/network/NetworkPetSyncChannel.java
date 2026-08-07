package com.petsistemi.integration.network;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Cross-server network sync channel interface (BungeeCord / Velocity / Redis PubSub)
 * enabling seamless pet persistence when players switch servers in a proxy network.
 */
public class NetworkPetSyncChannel {

    public static final String CHANNEL_NAME = "petsistemi:sync";

    private final JavaPlugin plugin;
    private final Logger logger;

    public NetworkPetSyncChannel(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin null olamaz.");
        this.logger = plugin.getLogger();
        registerChannel();
    }

    private void registerChannel() {
        if (plugin.getServer() != null && plugin.getServer().getMessenger() != null) {
            try {
                plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, CHANNEL_NAME);
                logger.info("Çapraz sunucu pet senkronizasyon kanalı (" + CHANNEL_NAME + ") aktif edildi.");
            } catch (Throwable t) {
                logger.warning("Çapraz sunucu kanalı kaydedilirken uyarı: " + t.getMessage());
            }
        }
    }

    public void dispatchPetSyncPayload(Player player, UUID petId, String action) {
        if (player == null || !player.isOnline() || petId == null) return;

        try (ByteArrayOutputStream b = new ByteArrayOutputStream();
             DataOutputStream out = new DataOutputStream(b)) {

            out.writeUTF(action != null ? action : "SYNC");
            out.writeUTF(player.getUniqueId().toString());
            out.writeUTF(petId.toString());

            player.sendPluginMessage(plugin, CHANNEL_NAME, b.toByteArray());
        } catch (Exception ignored) {}
    }
}

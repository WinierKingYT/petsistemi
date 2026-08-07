package com.petsistemi.gui;

import com.petsistemi.config.RuntimeConfigurationSnapshot;
import com.petsistemi.definition.PetDefinitionRegistry;
import com.petsistemi.domain.PetDefinition;
import com.petsistemi.domain.PetEmoteDefinition;
import com.petsistemi.message.MessageService;
import com.petsistemi.message.PlaceholderMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Interactive menu showing available emotes for the active pet.
 */
public class PetEmoteMenu {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    public static void open(
            Player viewer,
            UUID petId,
            JavaPlugin plugin,
            PetDefinitionRegistry definitionRegistry,
            AtomicReference<RuntimeConfigurationSnapshot> configSnapshot,
            MessageService messageService,
            PetDefinition definition
    ) {
        if (viewer == null || definition == null || definition.emotes() == null || definition.emotes().isEmpty()) {
            return;
        }

        Component title = text(messageService, "emote.menu-title", "<gold><bold>Pet Emoteleri</bold></gold>", null);
        PetMenuHolder holder = new PetMenuHolder("PET_EMOTE", 1);
        Inventory inv = Bukkit.createInventory(holder, 27, title);

        int slot = 10;
        for (Map.Entry<String, PetEmoteDefinition> entry : definition.emotes().entrySet()) {
            if (slot > 16) break;
            String emoteName = entry.getKey();
            PetEmoteDefinition emote = entry.getValue();

            List<Component> lore = new ArrayList<>();
            lore.add(MINI_MESSAGE.deserialize("<gray>Bekleme Süresi: <yellow>" + (emote.cooldownSeconds() > 0 ? emote.cooldownSeconds() + " sn" : "Yok") + "</yellow></gray>"));
            if (emote.sound() != null) {
                lore.add(MINI_MESSAGE.deserialize("<gray>Ses: <yellow>" + emote.sound() + "</yellow></gray>"));
            }
            lore.add(Component.text(""));
            lore.add(MINI_MESSAGE.deserialize("<yellow>⚡ Emote'u çalıştırmak için tıkla</yellow>"));

            ItemStack item = new ItemStack(Material.MUSIC_DISC_MELLOHI);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.displayName(MINI_MESSAGE.deserialize("<gold><bold>" + capitalize(emoteName) + "</bold></gold>"));
                meta.lore(lore);

                NamespacedKey actionKey = new NamespacedKey(plugin, "action");
                NamespacedKey petIdKey = new NamespacedKey(plugin, "pet_id");
                NamespacedKey emoteKey = new NamespacedKey(plugin, "emote_name");

                meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "trigger_emote");
                meta.getPersistentDataContainer().set(petIdKey, PersistentDataType.STRING, petId.toString());
                meta.getPersistentDataContainer().set(emoteKey, PersistentDataType.STRING, emoteName);

                item.setItemMeta(meta);
            }
            inv.setItem(slot++, item);
        }

        viewer.openInventory(inv);
    }

    private static Component text(MessageService service, String key, String fallback, PlaceholderMap placeholders) {
        if (service != null) {
            return service.getComponent(key, fallback, placeholders);
        }
        return MINI_MESSAGE.deserialize(fallback);
    }

    private static String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1).toLowerCase();
    }
}

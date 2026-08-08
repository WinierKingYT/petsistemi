package com.petsistemi.gui;

import com.petsistemi.api.PetService;
import com.petsistemi.api.PetSnapshot;
import com.petsistemi.bootstrap.MainThreadDispatcher;
import com.petsistemi.config.RuntimeConfigurationSnapshot;
import com.petsistemi.definition.PetDefinitionRegistry;
import com.petsistemi.domain.PetDefinition;
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
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 54-slot Chest GUI listing all pets owned by the player with pagination support.
 */
public class PetListMenu {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final int[] CONTENT_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    public static void open(
            Player viewer,
            PetService petService,
            JavaPlugin plugin,
            PetDefinitionRegistry definitionRegistry,
            AtomicReference<RuntimeConfigurationSnapshot> configSnapshot,
            MessageService messageService
    ) {
        open(viewer, petService, 0, plugin, definitionRegistry, configSnapshot, messageService);
    }

    public static void open(
            Player viewer,
            PetService petService,
            int page,
            JavaPlugin plugin,
            PetDefinitionRegistry definitionRegistry,
            AtomicReference<RuntimeConfigurationSnapshot> configSnapshot,
            MessageService messageService
    ) {
        if (viewer == null || petService == null) {
            return;
        }

        // Fetch off the main thread, render on it. The synchronous PetService#getOwnedPets is
        // a blocking join() on the single-threaded database executor, so opening the GUI used
        // to stall the whole server for the duration of the query (ADR 0003).
        java.util.concurrent.CompletableFuture<java.util.Collection<PetSnapshot>> petsFuture =
                petService instanceof com.petsistemi.api.AsyncPetService async
                        ? async.getOwnedPetsAsync(viewer.getUniqueId())
                        : java.util.concurrent.CompletableFuture.completedFuture(petService.getOwnedPets(viewer.getUniqueId()));

        petsFuture.thenAccept(pets -> {
            Runnable render = () -> {
                if (viewer.isOnline()) {
                    render(viewer, pets, page, plugin, definitionRegistry, messageService);
                }
            };
            if (plugin != null) {
                Bukkit.getScheduler().runTask(plugin, render);
            } else {
                render.run();
            }
        });
    }

    /** Builds and shows the inventory. Must run on the main thread. */
    private static void render(
            Player viewer,
            java.util.Collection<PetSnapshot> pets,
            int page,
            JavaPlugin plugin,
            PetDefinitionRegistry definitionRegistry,
            MessageService messageService
    ) {
        List<PetSnapshot> sortedPets = new ArrayList<>(pets);
        sortedPets.sort((a, b) -> {
            int stateA = a.spawned() ? 0 : (a.selected() ? 1 : 2);
            int stateB = b.spawned() ? 0 : (b.selected() ? 1 : 2);
            if (stateA != stateB) return Integer.compare(stateA, stateB);
            if (a.level() != b.level()) return Integer.compare(b.level(), a.level());
            return a.definitionId().compareToIgnoreCase(b.definitionId());
        });

        Component title = text(messageService, "list.menu-title", "<gold><bold>Pet Koleksiyonunuz</bold></gold>", null);
        int pageCount = Math.max(1, (sortedPets.size() + CONTENT_SLOTS.length - 1) / CONTENT_SLOTS.length);
        int safePage = Math.min(Math.max(0, page), pageCount - 1);
        PetMenuHolder holder = new PetMenuHolder("PET_LIST_ALL", safePage);
        Inventory inv = Bukkit.createInventory(holder, 54, title);

        int start = safePage * CONTENT_SLOTS.length;
        int end = Math.min(sortedPets.size(), start + CONTENT_SLOTS.length);
        for (int index = start; index < end; index++) {
            PetSnapshot pet = sortedPets.get(index);
            int slot = CONTENT_SLOTS[index - start];

            PetDefinition def = definitionRegistry != null ? definitionRegistry.find(pet.definitionId()).orElse(null) : null;
            Material mat = resolveMaterial(def != null ? def.guiMaterial() : null);

            String name = pet.customName() != null ? pet.customName() : (def != null ? def.displayName() : pet.definitionId());
            String shortId = pet.petId().toString().substring(0, 6);

            List<Component> lore = new ArrayList<>();
            lore.add(MINI_MESSAGE.deserialize("<gray>Tür: <yellow>" + pet.definitionId() + "</yellow></gray>"));
            lore.add(MINI_MESSAGE.deserialize("<gray>Seviye: <gold>" + pet.level() + "</gold></gray>"));
            lore.add(MINI_MESSAGE.deserialize("<gray>Deneyim: <yellow>" + pet.experience() + " XP</yellow></gray>"));
            lore.add(Component.text(""));
            if (pet.spawned()) {
                lore.add(MINI_MESSAGE.deserialize("<green>⚡ Şu Anda Çağrılmış (Aktif)</green>"));
            } else if (pet.selected()) {
                lore.add(MINI_MESSAGE.deserialize("<yellow>⭐ Seçili Pet</yellow>"));
            } else {
                lore.add(MINI_MESSAGE.deserialize("<gray>💤 Pasif</gray>"));
            }
            lore.add(Component.text(""));
            lore.add(MINI_MESSAGE.deserialize("<yellow>Sol Tık: Çağır / Gönder</yellow>"));
            lore.add(MINI_MESSAGE.deserialize("<yellow>Sağ Tık: İncele & Yönet</yellow>"));

            ItemStack item = new ItemStack(mat);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.displayName(MINI_MESSAGE.deserialize("<gold><bold>" + name + "</bold></gold> <gray>(" + shortId + ")</gray>"));
                meta.lore(lore);

                NamespacedKey actionKey = new NamespacedKey(plugin, "action");
                NamespacedKey petIdKey = new NamespacedKey(plugin, "pet_id");

                meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "select_pet");
                meta.getPersistentDataContainer().set(petIdKey, PersistentDataType.STRING, pet.petId().toString());

                item.setItemMeta(meta);
            }

            inv.setItem(slot, item);
        }

        if (safePage > 0) inv.setItem(45, navigationItem(Material.ARROW, "<yellow>Önceki Sayfa</yellow>"));
        inv.setItem(49, navigationItem(Material.BARRIER, "<red>Kapat</red>"));
        if (safePage + 1 < pageCount) inv.setItem(53, navigationItem(Material.ARROW, "<yellow>Sonraki Sayfa</yellow>"));

        viewer.openInventory(inv);
    }

    public static void openAsync(
            Player viewer,
            PetService petService,
            int page,
            JavaPlugin plugin,
            PetDefinitionRegistry definitionRegistry,
            MainThreadDispatcher dispatcher,
            AtomicReference<RuntimeConfigurationSnapshot> configSnapshot,
            MessageService messageService
    ) {
        // open() is already async-fetch / main-thread-render, so this is now a thin alias
        // kept for callers that pass a dispatcher.
        open(viewer, petService, page, plugin, definitionRegistry, configSnapshot, messageService);
    }

    private static Material resolveMaterial(String raw) {
        if (raw == null || raw.isBlank()) return Material.BONE;
        try {
            Material m = Material.matchMaterial(raw.trim());
            return m != null ? m : Material.BONE;
        } catch (Exception e) {
            return Material.BONE;
        }
    }

    private static ItemStack navigationItem(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(MINI_MESSAGE.deserialize(name));
            item.setItemMeta(meta);
        }
        return item;
    }

    private static Component text(MessageService service, String key, String fallback, PlaceholderMap placeholders) {
        if (service != null) {
            return service.getComponent(key, fallback, placeholders);
        }
        return MINI_MESSAGE.deserialize(fallback);
    }
}

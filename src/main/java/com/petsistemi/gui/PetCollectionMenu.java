package com.petsistemi.gui;

import com.petsistemi.api.AsyncPetService;
import com.petsistemi.api.PetService;
import com.petsistemi.api.PetSnapshot;
import com.petsistemi.definition.PetDefinitionRegistry;
import com.petsistemi.domain.PetDefinition;
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
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/** The definition-first collection catalogue, including locked pets. */
public final class PetCollectionMenu {

    public static final String ALL = "ALL";
    public static final String OWNED = "OWNED";
    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final int[] SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    private PetCollectionMenu() {}

    public static void open(Player viewer, PetService petService, JavaPlugin plugin,
                            PetDefinitionRegistry definitions, int page, String filter) {
        if (viewer == null || petService == null || plugin == null || definitions == null) return;
        var future = petService instanceof AsyncPetService async
                ? async.getOwnedPetsAsync(viewer.getUniqueId())
                : java.util.concurrent.CompletableFuture.completedFuture(petService.getOwnedPets(viewer.getUniqueId()));
        future.thenAccept(owned -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (viewer.isOnline()) render(viewer, plugin, definitions, owned, page, normalizeFilter(filter));
        }));
    }

    static void render(Player viewer, JavaPlugin plugin, PetDefinitionRegistry definitions,
                       Collection<PetSnapshot> owned, int page, String filter) {
        Map<String, List<PetSnapshot>> byDefinition = owned.stream().collect(Collectors.groupingBy(
                pet -> pet.definitionId().toLowerCase(Locale.ROOT)));
        List<PetDefinition> catalogue = new ArrayList<>(definitions.getAll());
        catalogue.sort(Comparator.comparing(PetDefinition::displayName, String.CASE_INSENSITIVE_ORDER));
        if (OWNED.equals(filter)) catalogue.removeIf(def -> !byDefinition.containsKey(def.id().toLowerCase(Locale.ROOT)));

        int pages = Math.max(1, (catalogue.size() + SLOTS.length - 1) / SLOTS.length);
        int safePage = Math.min(Math.max(0, page), pages - 1);
        Inventory inventory = Bukkit.createInventory(new PetMenuHolder("PET_COLLECTION", safePage, filter), 54,
                MINI.deserialize("<gold><bold>Pet Koleksiyonu</bold></gold> <gray>" + (safePage + 1) + "/" + pages + "</gray>"));

        int start = safePage * SLOTS.length;
        int end = Math.min(catalogue.size(), start + SLOTS.length);
        for (int index = start; index < end; index++) {
            PetDefinition definition = catalogue.get(index);
            List<PetSnapshot> copies = byDefinition.getOrDefault(definition.id().toLowerCase(Locale.ROOT), List.of());
            inventory.setItem(SLOTS[index - start], petItem(plugin, definition, copies));
        }

        if (safePage > 0) inventory.setItem(45, item(Material.ARROW, "<yellow>Önceki Sayfa</yellow>", List.of()));
        inventory.setItem(49, item(Material.BARRIER, "<red>Kapat</red>", List.of()));
        String nextFilter = ALL.equals(filter) ? OWNED : ALL;
        ItemStack filterItem = item(ALL.equals(filter) ? Material.CHEST : Material.ENDER_CHEST,
                "<aqua>Filtre: " + (ALL.equals(filter) ? "Tümü" : "Sahip Olduklarım") + "</aqua>",
                List.of(MINI.deserialize("<gray>Değiştirmek için tıkla</gray>")));
        setData(plugin, filterItem, "action", "collection_filter");
        setData(plugin, filterItem, "filter", nextFilter);
        inventory.setItem(50, filterItem);
        if (safePage + 1 < pages) inventory.setItem(53, item(Material.ARROW, "<yellow>Sonraki Sayfa</yellow>", List.of()));
        viewer.openInventory(inventory);
    }

    private static ItemStack petItem(JavaPlugin plugin, PetDefinition definition, List<PetSnapshot> copies) {
        boolean unlocked = !copies.isEmpty();
        Material material = resolveMaterial(definition.guiMaterial(), unlocked ? Material.BONE : Material.GRAY_DYE);
        List<Component> lore = new ArrayList<>();
        lore.add(MINI.deserialize("<gray>Tanım: <yellow>" + definition.id() + "</yellow></gray>"));
        lore.add(MINI.deserialize("<gray>Görünüm: <aqua>" + definition.representationOrEntity().key() + "</aqua></gray>"));
        if (definition.movement() != null) lore.add(MINI.deserialize("<gray>Hareket: <aqua>" + definition.movement().key() + "</aqua></gray>"));
        lore.add(Component.empty());
        if (unlocked) {
            int highest = copies.stream().map(PetSnapshot::level).max(Comparator.naturalOrder()).orElse(1);
            lore.add(MINI.deserialize("<green>✓ Açık</green> <gray>Adet: <white>" + copies.size() + "</white>, En yüksek: <gold>" + highest + "</gold></gray>"));
            lore.add(MINI.deserialize("<yellow>Sol Tık: İlk peti çağır</yellow>"));
            lore.add(MINI.deserialize("<yellow>Sağ Tık: İlk peti incele</yellow>"));
        } else {
            lore.add(MINI.deserialize("<red>🔒 Henüz açılmadı</red>"));
        }
        ItemStack item = item(material, (unlocked ? "<gold><bold>" : "<gray>") + definition.displayName()
                + (unlocked ? "</bold></gold>" : "</gray>"), lore);
        setData(plugin, item, "action", "collection_pet");
        setData(plugin, item, "definition_id", definition.id());
        if (unlocked) setData(plugin, item, "pet_id", copies.get(0).petId().toString());
        return item;
    }

    private static ItemStack item(Material material, String name, List<Component> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(MINI.deserialize(name));
            if (!lore.isEmpty()) meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static void setData(JavaPlugin plugin, ItemStack item, String key, String value) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, key), PersistentDataType.STRING, value);
        item.setItemMeta(meta);
    }

    private static Material resolveMaterial(String raw, Material fallback) {
        Material resolved = raw == null ? null : Material.matchMaterial(raw);
        return resolved != null ? resolved : fallback;
    }

    private static String normalizeFilter(String filter) {
        return OWNED.equalsIgnoreCase(filter) ? OWNED : ALL;
    }
}

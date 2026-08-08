package com.petsistemi.gui;

import com.petsistemi.definition.PetDefinitionRegistry;
import com.petsistemi.definition.editor.PetDefinitionEditorService;
import com.petsistemi.definition.editor.PetEditorField;
import com.petsistemi.definition.editor.PetEditorSessionManager;
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
import java.util.Comparator;
import java.util.List;

/** Inventory UI for selecting and editing pet definition drafts. */
public final class PetDefinitionEditorMenu {

    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final int[] SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    private PetDefinitionEditorMenu() {}

    public static void openCatalogue(Player player, JavaPlugin plugin, PetDefinitionRegistry registry, int page) {
        List<PetDefinition> definitions = new ArrayList<>(registry.getAll());
        definitions.sort(Comparator.comparing(PetDefinition::id));
        int pages = Math.max(1, (definitions.size() + SLOTS.length - 1) / SLOTS.length);
        int safePage = Math.min(Math.max(page, 0), pages - 1);
        Inventory inventory = Bukkit.createInventory(new PetMenuHolder("PET_EDITOR_LIST", safePage), 54,
                MINI.deserialize("<dark_aqua><bold>Pet Tanım Editörü</bold></dark_aqua> <gray>" + (safePage + 1) + "/" + pages + "</gray>"));
        int start = safePage * SLOTS.length;
        int end = Math.min(definitions.size(), start + SLOTS.length);
        for (int index = start; index < end; index++) {
            PetDefinition definition = definitions.get(index);
            ItemStack entry = item(resolveMaterial(definition.guiMaterial(), Material.WRITABLE_BOOK),
                    "<aqua><bold>" + definition.displayName() + "</bold></aqua>",
                    List.of(MINI.deserialize("<gray>Kimlik: <yellow>" + definition.id() + "</yellow></gray>"),
                            MINI.deserialize("<green>Düzenlemek için tıkla</green>")));
            data(plugin, entry, "action", "editor_open");
            data(plugin, entry, "definition_id", definition.id());
            inventory.setItem(SLOTS[index - start], entry);
        }
        if (safePage > 0) inventory.setItem(45, item(Material.ARROW, "<yellow>Önceki Sayfa</yellow>", List.of()));
        inventory.setItem(49, item(Material.BARRIER, "<red>Kapat</red>", List.of()));
        if (safePage + 1 < pages) inventory.setItem(53, item(Material.ARROW, "<yellow>Sonraki Sayfa</yellow>", List.of()));
        player.openInventory(inventory);
    }

    public static void openDefinition(Player player, JavaPlugin plugin, PetEditorSessionManager sessions) {
        PetDefinitionEditorService.Draft draft = sessions.draft(player.getUniqueId()).orElse(null);
        if (draft == null) {
            player.sendMessage(MINI.deserialize("<red>Editör oturumu bulunamadı veya süresi doldu.</red>"));
            return;
        }
        Inventory inventory = Bukkit.createInventory(new PetMenuHolder("PET_EDITOR_DETAIL", 0, draft.id()), 54,
                MINI.deserialize("<dark_aqua><bold>Düzenle:</bold></dark_aqua> <yellow>" + draft.id() + "</yellow>"));
        PetEditorField[] fields = PetEditorField.values();
        int[] fieldSlots = {10, 12, 14, 16, 28, 30};
        Material[] materials = {Material.NAME_TAG, Material.ITEM_FRAME, Material.ARMOR_STAND,
                Material.PAPER, Material.ZOMBIE_SPAWN_EGG, Material.FEATHER};
        for (int i = 0; i < fields.length; i++) {
            PetEditorField field = fields[i];
            ItemStack fieldItem = item(materials[i], "<aqua>" + field.label() + "</aqua>",
                    List.of(MINI.deserialize("<gray>Mevcut: <white>" + escape(draft.value(field)) + "</white></gray>"),
                            MINI.deserialize("<yellow>Chat ile değiştirmek için tıkla</yellow>"),
                            MINI.deserialize(field.removable() ? "<dark_gray>Silmek için '-' yazılabilir.</dark_gray>" : "<dark_gray>Boş bırakılamaz.</dark_gray>")));
            data(plugin, fieldItem, "action", "editor_input");
            data(plugin, fieldItem, "editor_field", field.name());
            inventory.setItem(fieldSlots[i], fieldItem);
        }

        boolean glowing = draft.yaml().getBoolean(draft.yaml().isConfigurationSection("representation")
                ? "representation.glowing" : "glowing", false);
        ItemStack glow = item(glowing ? Material.GLOWSTONE_DUST : Material.GUNPOWDER,
                "<aqua>Parlama: " + (glowing ? "<green>Açık</green>" : "<red>Kapalı</red>") + "</aqua>",
                List.of(MINI.deserialize("<yellow>Değiştirmek için tıkla</yellow>")));
        data(plugin, glow, "action", "editor_toggle_glowing");
        inventory.setItem(32, glow);

        ItemStack validate = item(Material.COMPASS, "<yellow>Taslağı Doğrula</yellow>", List.of());
        data(plugin, validate, "action", "editor_validate");
        inventory.setItem(46, validate);
        ItemStack back = item(Material.ARROW, "<gray>Taslağı At ve Geri Dön</gray>", List.of());
        data(plugin, back, "action", "editor_discard");
        inventory.setItem(48, back);
        inventory.setItem(49, item(Material.BARRIER, "<red>Kapat</red>", List.of()));
        ItemStack save = item(Material.LIME_CONCRETE, "<green><bold>Doğrula ve Kaydet</bold></green>",
                List.of(MINI.deserialize("<gray>Başarılı kayıt canlı kataloğa atomik yayımlanır.</gray>")));
        data(plugin, save, "action", "editor_save");
        inventory.setItem(52, save);
        player.openInventory(inventory);
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

    private static void data(JavaPlugin plugin, ItemStack item, String key, String value) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, key), PersistentDataType.STRING, value);
        item.setItemMeta(meta);
    }

    private static Material resolveMaterial(String raw, Material fallback) {
        Material material = raw == null ? null : Material.matchMaterial(raw);
        return material == null ? fallback : material;
    }

    private static String escape(String value) {
        return value.replace("<", "\\<").replace(">", "\\>");
    }
}

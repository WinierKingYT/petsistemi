package com.petsistemi.gui;

import com.petsistemi.api.PetService;
import com.petsistemi.api.PetSnapshot;
import com.petsistemi.definition.PetDefinitionRegistry;
import com.petsistemi.domain.PetAvailabilityState;
import com.petsistemi.domain.PetDefinition;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
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
import java.util.Optional;
import java.util.UUID;

public class PetListMenu {

    private static final int[] PET_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
    };

    public static void open(Player player, PetService petService, int page, JavaPlugin plugin) {
        open(player, petService, page, plugin, null);
    }

    public static void open(Player player, PetService petService, int page, JavaPlugin plugin, PetDefinitionRegistry definitionRegistry) {
        List<PetSnapshot> ownedPets = new ArrayList<>(petService.getOwnedPets(player.getUniqueId()));
        Optional<PetSnapshot> spawnedPet = petService.getSpawnedPet(player.getUniqueId());
        UUID spawnedPetId = spawnedPet.map(PetSnapshot::petId).orElse(null);

        int pageSize = PET_SLOTS.length;
        int totalPages = Math.max(1, (int) Math.ceil((double) ownedPets.size() / pageSize));
        int currentPage = Math.max(0, Math.min(page, totalPages - 1));

        Inventory inv = Bukkit.createInventory(
                new PetMenuHolder("PET_LIST", currentPage),
                54,
                Component.text("Petleriniz (Sayfa " + (currentPage + 1) + "/" + totalPages + ")", NamedTextColor.GOLD, TextDecoration.BOLD)
        );

        // Fill glass pane borders
        ItemStack border = createBorderItem();
        for (int i = 0; i < 9; i++) inv.setItem(i, border);
        for (int i = 45; i < 54; i++) inv.setItem(i, border);
        inv.setItem(9, border); inv.setItem(17, border);
        inv.setItem(18, border); inv.setItem(26, border);
        inv.setItem(27, border); inv.setItem(35, border);
        inv.setItem(36, border); inv.setItem(44, border);

        int startIndex = currentPage * pageSize;
        int endIndex = Math.min(startIndex + pageSize, ownedPets.size());

        for (int i = startIndex; i < endIndex; i++) {
            PetSnapshot pet = ownedPets.get(i);
            int slot = PET_SLOTS[i - startIndex];
            inv.setItem(slot, createPetItem(pet, spawnedPetId, plugin, definitionRegistry));
        }

        // Navigation
        if (currentPage > 0) {
            inv.setItem(45, createNavItem(Material.ARROW, "◀ Önceki Sayfa (" + currentPage + ")", NamedTextColor.YELLOW));
        }
        if (currentPage < totalPages - 1) {
            inv.setItem(53, createNavItem(Material.ARROW, "Sonraki Sayfa ▶ (" + (currentPage + 2) + ")", NamedTextColor.YELLOW));
        }

        // Info / Stats Item
        inv.setItem(48, createInfoItem(ownedPets.size(), spawnedPet.isPresent()));
        inv.setItem(49, createNavItem(Material.BARRIER, "Menüyü Kapat", NamedTextColor.RED));

        player.openInventory(inv);
    }

    private static ItemStack createPetItem(PetSnapshot pet, UUID spawnedPetId, JavaPlugin plugin, PetDefinitionRegistry definitionRegistry) {
        Material material = switch (pet.definitionId().toLowerCase()) {
            case "wolf" -> Material.WOLF_SPAWN_EGG;
            case "cat" -> Material.CAT_SPAWN_EGG;
            case "allay" -> Material.ALLAY_SPAWN_EGG;
            default -> Material.BONE;
        };

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String displayName = pet.customName() != null ? pet.customName() : formatDefaultName(pet.definitionId());
            meta.displayName(Component.text(displayName, NamedTextColor.GOLD, TextDecoration.BOLD));

            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("Tür: ", NamedTextColor.GRAY).append(Component.text(capitalize(pet.definitionId()), NamedTextColor.WHITE)));
            lore.add(Component.text("Seviye: ", NamedTextColor.GRAY).append(Component.text(pet.level(), NamedTextColor.YELLOW, TextDecoration.BOLD)));

            // XP Progress Bar
            long currentXp = pet.experience();
            long nextLevelXp = (long) pet.level() * 100L;
            int progress = (int) Math.min(10, Math.max(0, (currentXp % 100) / 10));
            String bar = "■".repeat(progress) + "□".repeat(10 - progress);
            lore.add(Component.text("XP: [" + bar + "] " + currentXp + " XP", NamedTextColor.AQUA));

            lore.add(Component.empty());

            boolean isSpawned = spawnedPetId != null && spawnedPetId.equals(pet.petId());
            if (pet.availabilityState() == PetAvailabilityState.DISABLED) {
                lore.add(Component.text("✖ Durum: DEVRE DIŞI", NamedTextColor.RED, TextDecoration.BOLD));
                lore.add(Component.text("Bu pet yönetici tarafından kapatılmıştır.", NamedTextColor.DARK_GRAY));
            } else if (isSpawned) {
                lore.add(Component.text("✔ Durum: ÇAĞIRILDI (AKTİF)", NamedTextColor.GREEN, TextDecoration.BOLD));
                lore.add(Component.text("▶ Sol Tık: Peti Geri Gönder (Dismiss)", NamedTextColor.LIGHT_PURPLE));
            } else {
                lore.add(Component.text("⚡ Durum: KULLANILABİLİR", NamedTextColor.YELLOW));
                lore.add(Component.text("▶ Sol Tık: Peti Çağır (Summon)", NamedTextColor.GREEN));
            }

            lore.add(Component.text("▶ Shift + Sol Tık: İsim Değiştir", NamedTextColor.AQUA));
            lore.add(Component.empty());
            lore.add(Component.text("ID: " + pet.petId().toString().substring(0, 8), NamedTextColor.DARK_GRAY));

            meta.lore(lore);

            if (plugin != null) {
                meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "pet_id"), PersistentDataType.STRING, pet.petId().toString());
            }

            item.setItemMeta(meta);
        }
        return item;
    }

    private static ItemStack createBorderItem() {
        ItemStack item = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(" ", NamedTextColor.DARK_GRAY));
            item.setItemMeta(meta);
        }
        return item;
    }

    private static ItemStack createNavItem(Material material, String name, NamedTextColor color) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(name, color, TextDecoration.BOLD));
            item.setItemMeta(meta);
        }
        return item;
    }

    private static ItemStack createInfoItem(int totalPets, boolean activeSpawned) {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("Pet İstatistikleriniz", NamedTextColor.GOLD, TextDecoration.BOLD));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("Sahip Olunan Petler: ", NamedTextColor.GRAY).append(Component.text(totalPets, NamedTextColor.YELLOW)));
            lore.add(Component.text("Aktif Çağırılmış Pet: ", NamedTextColor.GRAY).append(Component.text(activeSpawned ? "Evet" : "Hayır", activeSpawned ? NamedTextColor.GREEN : NamedTextColor.RED)));
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static String formatDefaultName(String defId) {
        return capitalize(defId) + " Dostu";
    }

    private static String capitalize(String str) {
        if (str == null || str.isEmpty()) return "";
        return str.substring(0, 1).toUpperCase() + str.substring(1).toLowerCase();
    }
}

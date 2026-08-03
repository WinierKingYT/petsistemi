package com.petsistemi.gui;

import com.petsistemi.api.PetService;
import com.petsistemi.api.PetSnapshot;
import com.petsistemi.domain.PetAvailabilityState;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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

    public static void open(Player player, PetService petService, int page, JavaPlugin plugin) {
        List<PetSnapshot> ownedPets = new ArrayList<>(petService.getOwnedPets(player.getUniqueId()));
        Optional<PetSnapshot> spawnedPet = petService.getSpawnedPet(player.getUniqueId());
        UUID spawnedPetId = spawnedPet.map(PetSnapshot::petId).orElse(null);

        int pageSize = 36;
        int totalPages = Math.max(1, (int) Math.ceil((double) ownedPets.size() / pageSize));
        int currentPage = Math.max(0, Math.min(page, totalPages - 1));

        Inventory inv = Bukkit.createInventory(
                new PetMenuHolder("PET_LIST", currentPage),
                54,
                Component.text("Petleriniz (Sayfa " + (currentPage + 1) + "/" + totalPages + ")", NamedTextColor.DARK_BLUE)
        );

        int startIndex = currentPage * pageSize;
        int endIndex = Math.min(startIndex + pageSize, ownedPets.size());

        for (int i = startIndex; i < endIndex; i++) {
            PetSnapshot pet = ownedPets.get(i);
            inv.setItem(i - startIndex, createPetItem(pet, spawnedPetId, plugin));
        }

        if (currentPage > 0) {
            inv.setItem(45, createNavItem(Material.ARROW, "Önceki Sayfa (" + currentPage + ")"));
        }
        if (currentPage < totalPages - 1) {
            inv.setItem(53, createNavItem(Material.ARROW, "Sonraki Sayfa (" + (currentPage + 2) + ")"));
        }

        inv.setItem(49, createNavItem(Material.BARRIER, "Menüyü Kapat"));

        player.openInventory(inv);
    }

    private static ItemStack createPetItem(PetSnapshot pet, UUID spawnedPetId, JavaPlugin plugin) {
        ItemStack item = new ItemStack(Material.BONE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String name = pet.customName() != null ? pet.customName() : pet.definitionId();
            meta.displayName(Component.text(name, NamedTextColor.GOLD));

            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("Tür: " + pet.definitionId(), NamedTextColor.GRAY));
            lore.add(Component.text("Seviye: " + pet.level(), NamedTextColor.YELLOW));
            lore.add(Component.text("Deneyim: " + pet.experience(), NamedTextColor.YELLOW));

            boolean isSpawned = spawnedPetId != null && spawnedPetId.equals(pet.petId());
            if (pet.availabilityState() == PetAvailabilityState.DISABLED) {
                lore.add(Component.text("Durum: DEVRE DIŞI", NamedTextColor.RED));
            } else if (isSpawned) {
                lore.add(Component.text("Durum: ÇAĞIRILMIŞ (SPAWNED)", NamedTextColor.GREEN));
                lore.add(Component.text("▶ Sol Tık: Peti Geri Gönder", NamedTextColor.LIGHT_PURPLE));
            } else {
                lore.add(Component.text("Durum: KULLANILABİLİR", NamedTextColor.GRAY));
                lore.add(Component.text("▶ Sol Tık: Peti Çağır", NamedTextColor.GREEN));
            }

            lore.add(Component.text("▶ Shift + Sol Tık: İsim Değiştir", NamedTextColor.AQUA));
            lore.add(Component.text("Pet ID: " + pet.petId().toString().substring(0, 8), NamedTextColor.DARK_GRAY));
            meta.lore(lore);

            if (plugin != null) {
                meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "pet_id"), PersistentDataType.STRING, pet.petId().toString());
            }

            item.setItemMeta(meta);
        }
        return item;
    }

    private static ItemStack createNavItem(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(name, NamedTextColor.YELLOW));
            item.setItemMeta(meta);
        }
        return item;
    }
}

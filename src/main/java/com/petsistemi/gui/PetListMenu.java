package com.petsistemi.gui;

import com.petsistemi.api.AsyncPetService;
import com.petsistemi.api.PetService;
import com.petsistemi.api.PetSnapshot;
import com.petsistemi.bootstrap.MainThreadDispatcher;
import com.petsistemi.config.RuntimeConfigurationSnapshot;
import com.petsistemi.definition.PetDefinitionRegistry;
import com.petsistemi.domain.PetAvailabilityState;
import com.petsistemi.domain.PetDefinition;
import com.petsistemi.message.MessageService;
import com.petsistemi.message.PlaceholderMap;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

public class PetListMenu {

    private static final int[] PET_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
    };

    private static final String DEFAULT_GUI_TITLE = "Pet Menüsü";

    public static void open(Player player, PetService petService, int page, JavaPlugin plugin) {
        open(player, petService, page, plugin, null);
    }

    public static void open(Player player, PetService petService, int page, JavaPlugin plugin, PetDefinitionRegistry definitionRegistry) {
        openAsync(player, petService, page, plugin, definitionRegistry, null, null);
    }

    public static void open(Player player, PetService petService, int page, JavaPlugin plugin, PetDefinitionRegistry definitionRegistry, AtomicReference<RuntimeConfigurationSnapshot> configSnapshot) {
        openAsync(player, petService, page, plugin, definitionRegistry, null, configSnapshot, null);
    }

    public static void open(Player player, PetService petService, int page, JavaPlugin plugin, PetDefinitionRegistry definitionRegistry, AtomicReference<RuntimeConfigurationSnapshot> configSnapshot, MessageService messageService) {
        openAsync(player, petService, page, plugin, definitionRegistry, null, configSnapshot, messageService);
    }

    public static void openAsync(Player player, PetService petService, int page, JavaPlugin plugin, PetDefinitionRegistry definitionRegistry, MainThreadDispatcher dispatcher) {
        openAsync(player, petService, page, plugin, definitionRegistry, dispatcher, null, null);
    }

    public static void openAsync(Player player, PetService petService, int page, JavaPlugin plugin, PetDefinitionRegistry definitionRegistry, MainThreadDispatcher dispatcher, AtomicReference<RuntimeConfigurationSnapshot> configSnapshot) {
        openAsync(player, petService, page, plugin, definitionRegistry, dispatcher, configSnapshot, null);
    }

    public static void openAsync(Player player, PetService petService, int page, JavaPlugin plugin, PetDefinitionRegistry definitionRegistry, MainThreadDispatcher dispatcher, AtomicReference<RuntimeConfigurationSnapshot> configSnapshot, MessageService messageService) {
        if (player == null || !player.isOnline()) return;
        UUID ownerId = player.getUniqueId();

        CompletableFuture<List<PetSnapshot>> petsFuture;
        if (petService instanceof AsyncPetService asyncPetService) {
            petsFuture = asyncPetService.getOwnedPetsAsync(ownerId).thenApply(ArrayList::new);
        } else {
            petsFuture = CompletableFuture.completedFuture(new ArrayList<>(petService.getOwnedPets(ownerId)));
        }

        petsFuture.thenAccept(ownedPets -> {
            Runnable renderAction = () -> {
                if (player.isOnline()) {
                    renderAndOpen(player, ownedPets, page, plugin, definitionRegistry, configSnapshot, messageService);
                }
            };

            if (dispatcher != null) {
                dispatcher.run(renderAction);
            } else if (plugin != null) {
                Bukkit.getScheduler().runTask(plugin, renderAction);
            } else {
                renderAction.run();
            }
        });
    }

    public static void renderAndOpen(Player player, List<PetSnapshot> ownedPets, int page, JavaPlugin plugin, PetDefinitionRegistry definitionRegistry, AtomicReference<RuntimeConfigurationSnapshot> configSnapshot) {
        renderAndOpen(player, ownedPets, page, plugin, definitionRegistry, configSnapshot, null);
    }

    public static void renderAndOpen(Player player, List<PetSnapshot> ownedPets, int page, JavaPlugin plugin, PetDefinitionRegistry definitionRegistry, AtomicReference<RuntimeConfigurationSnapshot> configSnapshot, MessageService messageService) {
        if (player == null || !player.isOnline()) return;

        List<PetSnapshot> safeList = ownedPets != null ? ownedPets : List.of();
        Optional<PetSnapshot> spawnedPet = safeList.stream().filter(PetSnapshot::spawned).findFirst();
        UUID spawnedPetId = spawnedPet.map(PetSnapshot::petId).orElse(null);

        int pageSize = PET_SLOTS.length;
        int totalPages = Math.max(1, (int) Math.ceil((double) safeList.size() / pageSize));
        int currentPage = Math.max(0, Math.min(page, totalPages - 1));

        String guiTitle = guiTitle(configSnapshot);
        Inventory inv = Bukkit.createInventory(
                new PetMenuHolder("PET_LIST", currentPage),
                54,
                Component.text(guiTitle, NamedTextColor.GOLD, TextDecoration.BOLD)
                        .append(text(messageService, "gui.page-suffix", " <dark_gray>(Sayfa <page>/<total>)</dark_gray>",
                                PlaceholderMap.of("page", String.valueOf(currentPage + 1)).add("total", String.valueOf(totalPages))))
        );

        ItemStack border = createBorderItem();
        for (int i = 0; i < 9; i++) inv.setItem(i, border);
        for (int i = 45; i < 54; i++) inv.setItem(i, border);
        inv.setItem(9, border); inv.setItem(17, border);
        inv.setItem(18, border); inv.setItem(26, border);
        inv.setItem(27, border); inv.setItem(35, border);
        inv.setItem(36, border); inv.setItem(44, border);

        int startIndex = currentPage * pageSize;
        int endIndex = Math.min(startIndex + pageSize, safeList.size());

        for (int i = startIndex; i < endIndex; i++) {
            PetSnapshot pet = safeList.get(i);
            int slot = PET_SLOTS[i - startIndex];
            inv.setItem(slot, createPetItem(pet, spawnedPetId, plugin, definitionRegistry, configSnapshot, messageService));
        }

        if (currentPage > 0) {
            inv.setItem(45, createNavItem(Material.ARROW,
                    text(messageService, "gui.prev-page", "<yellow>◀ Önceki Sayfa (<page>)</yellow>", PlaceholderMap.of("page", String.valueOf(currentPage)))));
        }
        if (currentPage < totalPages - 1) {
            inv.setItem(53, createNavItem(Material.ARROW,
                    text(messageService, "gui.next-page", "<yellow>Sonraki Sayfa ▶ (<page>)</yellow>", PlaceholderMap.of("page", String.valueOf(currentPage + 2)))));
        }

        inv.setItem(48, createInfoItem(safeList.size(), spawnedPet.isPresent(), configSnapshot, messageService));
        inv.setItem(49, createNavItem(Material.BARRIER, text(messageService, "gui.close", "<red>Menüyü Kapat</red>", null)));

        player.openInventory(inv);
    }

    private static Component text(MessageService messageService, String key, String fallback, PlaceholderMap placeholders) {
        if (messageService != null) {
            return messageService.getComponent(key, fallback, placeholders);
        }
        return com.petsistemi.message.MiniMessageRenderer.render(fallback, placeholders);
    }

    private static String guiTitle(AtomicReference<RuntimeConfigurationSnapshot> configSnapshot) {
        if (configSnapshot != null
                && configSnapshot.get() != null
                && configSnapshot.get().configuration() != null
                && configSnapshot.get().configuration().gui() != null
                && configSnapshot.get().configuration().gui().title() != null
                && !configSnapshot.get().configuration().gui().title().isEmpty()) {
            return configSnapshot.get().configuration().gui().title();
        }
        return DEFAULT_GUI_TITLE;
    }

    private static long xpPerLevel(AtomicReference<RuntimeConfigurationSnapshot> configSnapshot) {
        if (configSnapshot != null
                && configSnapshot.get() != null
                && configSnapshot.get().configuration() != null
                && configSnapshot.get().configuration().progression() != null) {
            long xp = configSnapshot.get().configuration().progression().xpPerLevel();
            return xp > 0 ? xp : 100L;
        }
        return 100L;
    }

    private static int maxPets(AtomicReference<RuntimeConfigurationSnapshot> configSnapshot) {
        if (configSnapshot != null
                && configSnapshot.get() != null
                && configSnapshot.get().configuration() != null
                && configSnapshot.get().configuration().limits() != null) {
            int max = configSnapshot.get().configuration().limits().maximumOwnedPets();
            return max > 0 ? max : 20;
        }
        return 20;
    }

    private static ItemStack createPetItem(PetSnapshot pet, UUID spawnedPetId, JavaPlugin plugin, PetDefinitionRegistry definitionRegistry, AtomicReference<RuntimeConfigurationSnapshot> configSnapshot, MessageService messageService) {
        PetDefinition definition = definitionRegistry != null
                ? definitionRegistry.find(pet.definitionId()).orElse(null)
                : null;
        ItemStack item = new ItemStack(PetMenuIcons.resolve(definition, pet.definitionId()));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String customName = pet.customName();
            Component displayComponent;
            if (customName != null && !customName.isEmpty()) {
                displayComponent = com.petsistemi.util.LegacyColorTranslator.hasCodes(customName)
                        ? com.petsistemi.util.LegacyColorTranslator.toComponent(customName)
                        : Component.text(customName, NamedTextColor.GOLD, TextDecoration.BOLD);
            } else {
                displayComponent = text(messageService, "gui.default-name", "<gold><b><name> Dostu</b></gold>",
                        PlaceholderMap.of("name", capitalize(pet.definitionId())));
            }
            meta.displayName(displayComponent);

            List<Component> lore = new ArrayList<>();
            lore.add(text(messageService, "gui.pet-type-line", "<gray>Tür: </gray><white><type></white>", PlaceholderMap.of("type", capitalize(pet.definitionId()))));
            lore.add(text(messageService, "gui.pet-level-line", "<gray>Seviye: </gray><yellow><b><level></b></yellow>", PlaceholderMap.of("level", String.valueOf(pet.level()))));

            long currentXp = pet.experience();
            long xpPerLevel = xpPerLevel(configSnapshot);
            long xpThisLevel = currentXp - (long)(pet.level() - 1) * xpPerLevel;
            long xpToNext = xpPerLevel;
            xpThisLevel = Math.max(0, Math.min(xpThisLevel, xpToNext));
            int progress = (int) Math.round((double) xpThisLevel / xpToNext * 10.0);
            progress = Math.max(0, Math.min(10, progress));
            String filledBar = "■".repeat(progress);
            String emptyBar = "□".repeat(10 - progress);
            lore.add(text(messageService, "gui.pet-xp-line", "<gray>XP: [</gray><green><filled></green><dark_green><empty></dark_green><gray>] </gray><aqua><current>/<needed> XP</aqua>",
                    PlaceholderMap.of("filled", filledBar).add("empty", emptyBar)
                            .add("current", String.valueOf(xpThisLevel)).add("needed", String.valueOf(xpToNext))));

            lore.add(Component.empty());

            boolean isSpawned = spawnedPetId != null && spawnedPetId.equals(pet.petId());
            if (pet.availabilityState() == PetAvailabilityState.DISABLED) {
                lore.add(text(messageService, "gui.status-disabled-title", "<red><b>✖ Durum: DEVRE DIŞI</b></red>", null));
                lore.add(text(messageService, "gui.status-disabled-desc", "<dark_gray>Bu pet yönetici tarafından kapatılmıştır.</dark_gray>", null));
            } else if (isSpawned) {
                lore.add(text(messageService, "gui.status-active-title", "<green><b>✔ Durum: ÇAĞIRILDI (AKTİF)</b></green>", null));
                lore.add(text(messageService, "gui.status-active-hint", "<light_purple>▶ Sol Tık: Peti Geri Gönder (Dismiss)</light_purple>", null));
            } else {
                lore.add(text(messageService, "gui.status-available-title", "<yellow>⚡ Durum: KULLANILABİLİR</yellow>", null));
                lore.add(text(messageService, "gui.status-available-hint", "<green>▶ Sol Tık: Peti Çağır (Summon)</green>", null));
            }

            lore.add(text(messageService, "gui.rename-hint", "<aqua>▶ Shift + Sol Tık: İsim Değiştir</aqua>", null));
            lore.add(Component.empty());
            lore.add(text(messageService, "gui.pet-id-line", "<dark_gray>ID: <pet_id></dark_gray>", PlaceholderMap.of("pet_id", pet.petId().toString().substring(0, 8))));

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

    private static ItemStack createNavItem(Material material, Component name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(name);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static ItemStack createInfoItem(int totalPets, boolean activeSpawned, AtomicReference<RuntimeConfigurationSnapshot> configSnapshot, MessageService messageService) {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(text(messageService, "gui.info-title", "<gold><b>Pet İstatistikleriniz</b></gold>", null));
            int maxPets = maxPets(configSnapshot);
            List<Component> lore = new ArrayList<>();
            lore.add(text(messageService, "gui.info-owned-line", "<gray>Sahip Olunan Petler: </gray><yellow><count>/<max></yellow>",
                    PlaceholderMap.of("count", String.valueOf(totalPets)).add("max", String.valueOf(maxPets))));
            lore.add(text(messageService, "gui.info-active-prefix", "<gray>Aktif Çağırılmış Pet: </gray>", null)
                    .append(text(messageService, activeSpawned ? "gui.yes" : "gui.no",
                            activeSpawned ? "<green>Evet</green>" : "<red>Hayır</red>", null)));
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static String capitalize(String str) {
        if (str == null || str.isEmpty()) return "";
        return str.substring(0, 1).toUpperCase() + str.substring(1).toLowerCase();
    }
}

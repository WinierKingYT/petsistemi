package com.petsistemi.gui;

import com.petsistemi.api.AsyncPetService;
import com.petsistemi.api.PetService;
import com.petsistemi.api.PetSnapshot;
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
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Read-only pet inspection GUI (opened by right-clicking any pet entity).
 */
public class PetInspectMenu {

    public static void open(Player viewer, PetService petService, UUID petId, UUID ownerId,
                            JavaPlugin plugin, PetDefinitionRegistry definitionRegistry,
                            AtomicReference<RuntimeConfigurationSnapshot> configSnapshot) {
        open(viewer, petService, petId, ownerId, plugin, definitionRegistry, configSnapshot, null);
    }

    public static void open(Player viewer, PetService petService, UUID petId, UUID ownerId,
                            JavaPlugin plugin, PetDefinitionRegistry definitionRegistry,
                            AtomicReference<RuntimeConfigurationSnapshot> configSnapshot, MessageService messageService) {
        if (viewer == null || !viewer.isOnline() || petService == null) return;

        CompletableFuture<Optional<PetSnapshot>> petFuture = petService instanceof AsyncPetService async
                ? async.findPetAsync(petId)
                : CompletableFuture.completedFuture(petService.findPet(petId));

        petFuture.thenAccept(petOpt -> {
            Runnable renderAction = () -> {
                if (viewer.isOnline()) {
                    renderAndOpen(viewer, petOpt.orElse(null), ownerId, plugin, definitionRegistry, configSnapshot, messageService);
                }
            };
            if (plugin != null) {
                Bukkit.getScheduler().runTask(plugin, renderAction);
            } else {
                renderAction.run();
            }
        });
    }

    public static void renderAndOpen(Player viewer, PetSnapshot pet, UUID ownerId,
                                     JavaPlugin plugin, PetDefinitionRegistry definitionRegistry,
                                     AtomicReference<RuntimeConfigurationSnapshot> configSnapshot) {
        renderAndOpen(viewer, pet, ownerId, plugin, definitionRegistry, configSnapshot, null);
    }

    public static void renderAndOpen(Player viewer, PetSnapshot pet, UUID ownerId,
                                     JavaPlugin plugin, PetDefinitionRegistry definitionRegistry,
                                     AtomicReference<RuntimeConfigurationSnapshot> configSnapshot,
                                     MessageService messageService) {
        if (viewer == null || !viewer.isOnline()) return;

        Inventory inv = Bukkit.createInventory(
                new PetMenuHolder("PET_INSPECT", 0),
                27,
                text(messageService, "inspect.gui-title", "<gold><b>Pet Bilgileri</b></gold>", null)
        );

        if (pet == null) {
            inv.setItem(13, createInfoItem(Material.BARRIER,
                    text(messageService, "inspect.no-data-title", "<red><b>Pet Verisi Yok</b></red>", null),
                    List.of(text(messageService, "inspect.not-found", "<red>Bu pet veritabanında bulunamadı.</red>", null))));
            viewer.openInventory(inv);
            return;
        }

        PetDefinition def = definitionRegistry != null ? definitionRegistry.find(pet.definitionId()).orElse(null) : null;
        OfflinePlayer owner = Bukkit.getOfflinePlayer(ownerId);
        String ownerName = owner.getName() != null ? owner.getName() : ownerId.toString().substring(0, 8);

        String displayName = pet.customName() != null ? pet.customName() : pet.definitionId();
        Component nameComponent = com.petsistemi.util.LegacyColorTranslator.hasCodes(displayName)
                ? com.petsistemi.util.LegacyColorTranslator.toComponent(displayName)
                : Component.text(displayName, NamedTextColor.GOLD, TextDecoration.BOLD);
        inv.setItem(10, createItem(Material.PLAYER_HEAD, nameComponent, List.of(
                text(messageService, "inspect.owner-line", "<gray>Sahip: </gray><white><player></white>", PlaceholderMap.of("player", ownerName)),
                text(messageService, "gui.pet-id-line", "<dark_gray>ID: <pet_id></dark_gray>", PlaceholderMap.of("pet_id", pet.petId().toString().substring(0, 8))))));

        Material typeMaterial = PetMenuIcons.resolve(def, pet.definitionId());
        String typeLabel = def != null ? def.displayName() : pet.definitionId();
        inv.setItem(12, createItem(typeMaterial, Component.text(typeLabel, NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD), List.of(
                text(messageService, "inspect.type-line", "<gray>Tür: </gray><white><type></white>", PlaceholderMap.of("type", pet.definitionId())))));

        long xpPerLevel = xpPerLevelFromSnapshot(configSnapshot);
        long xpThisLevel = Math.max(0, Math.min(pet.experience() - (long) (pet.level() - 1) * xpPerLevel, xpPerLevel));
        int progress = Math.max(0, Math.min(10, (int) Math.round((double) xpThisLevel / xpPerLevel * 10.0)));
        String filledBar = "■".repeat(progress);
        String emptyBar = "□".repeat(10 - progress);

        inv.setItem(14, createItem(Material.EXPERIENCE_BOTTLE,
                text(messageService, "inspect.level-name", "<green><b>Seviye <level></b></green>", PlaceholderMap.of("level", String.valueOf(pet.level()))),
                List.of(text(messageService, "gui.pet-xp-line", "<gray>XP: [</gray><green><filled></green><dark_green><empty></dark_green><gray>] </gray><aqua><current>/<needed> XP</aqua>",
                        PlaceholderMap.of("filled", filledBar).add("empty", emptyBar)
                                .add("current", String.valueOf(xpThisLevel)).add("needed", String.valueOf(xpPerLevel))))));

        boolean disabled = pet.availabilityState() == PetAvailabilityState.DISABLED;
        String stateKey = disabled ? "inspect.status-disabled" : (pet.spawned() ? "inspect.status-spawned" : "inspect.status-available");
        String stateFallback = disabled ? "<red><b>DEVRE DIŞI</b></red>" : (pet.spawned() ? "<green><b>ÇAĞIRILDI</b></green>" : "<yellow><b>HAZIR</b></yellow>");
        inv.setItem(16, createItem(Material.STICK,
                text(messageService, "inspect.status-title", "<gold>Durum</gold>", null),
                List.of(
                        text(messageService, stateKey, stateFallback, null),
                        text(messageService, "inspect.selected-prefix", "<gray>Seçili: </gray>", null)
                                .append(text(messageService, pet.selected() ? "gui.yes" : "gui.no",
                                        pet.selected() ? "<green>Evet</green>" : "<red>Hayır</red>", null)))));

        viewer.openInventory(inv);
    }

    private static Component text(MessageService messageService, String key, String fallback, PlaceholderMap placeholders) {
        if (messageService != null) {
            return messageService.getComponent(key, fallback, placeholders);
        }
        return com.petsistemi.message.MiniMessageRenderer.render(fallback, placeholders);
    }

    private static long xpPerLevelFromSnapshot(AtomicReference<RuntimeConfigurationSnapshot> configSnapshot) {
        if (configSnapshot != null
                && configSnapshot.get() != null
                && configSnapshot.get().configuration() != null
                && configSnapshot.get().configuration().progression() != null) {
            long xp = configSnapshot.get().configuration().progression().xpPerLevel();
            return xp > 0 ? xp : 100L;
        }
        return 100L;
    }

    private static ItemStack createItem(Material material, Component name, List<Component> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(name);
            if (lore != null && !lore.isEmpty()) {
                meta.lore(lore);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private static ItemStack createInfoItem(Material material, Component name, List<Component> lore) {
        return createItem(material, name, lore);
    }
}

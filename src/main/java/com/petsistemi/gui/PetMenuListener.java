package com.petsistemi.gui;

import com.petsistemi.api.PetService;
import com.petsistemi.api.PetSnapshot;
import com.petsistemi.definition.PetDefinitionRegistry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PetMenuListener implements Listener {

    /** Cooldown in milliseconds between successive clicks for the same player. */
    private static final long CLICK_COOLDOWN_MS = 500L;

    private final JavaPlugin plugin;
    private final PetService petService;
    private final PlayerInputSessionManager sessionManager;
    private final PetDefinitionRegistry definitionRegistry;

    /** Debounce set: tracks players currently inside an action to prevent double-clicks. */
    private final Set<UUID> processingPlayers = ConcurrentHashMap.newKeySet();
    /** Last-click timestamp per player for cooldown enforcement. */
    private final ConcurrentHashMap<UUID, Long> lastClickTime = new ConcurrentHashMap<>();

    public PetMenuListener(JavaPlugin plugin, PetService petService, PlayerInputSessionManager sessionManager) {
        this(plugin, petService, sessionManager, null);
    }

    public PetMenuListener(JavaPlugin plugin, PetService petService,
                           PlayerInputSessionManager sessionManager,
                           PetDefinitionRegistry definitionRegistry) {
        this.plugin = plugin;
        this.petService = petService;
        this.sessionManager = sessionManager;
        this.definitionRegistry = definitionRegistry;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof PetMenuHolder holder)) return;

        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;

        UUID uuid = player.getUniqueId();

        // ── Debounce guard ──────────────────────────────────────────────────────
        long now = System.currentTimeMillis();
        Long last = lastClickTime.get(uuid);
        if (last != null && (now - last) < CLICK_COOLDOWN_MS) return;
        if (!processingPlayers.add(uuid)) return; // already handling a click
        lastClickTime.put(uuid, now);

        try {
            handleClick(player, holder, event);
        } finally {
            processingPlayers.remove(uuid);
        }
    }

    private void handleClick(Player player, PetMenuHolder holder, InventoryClickEvent event) {
        int slot = event.getRawSlot();

        // Navigation: Previous page
        if (slot == 45 && holder.page() > 0) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
            PetListMenu.open(player, petService, holder.page() - 1, plugin, definitionRegistry);
            return;
        }
        // Navigation: Next page
        if (slot == 53) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
            PetListMenu.open(player, petService, holder.page() + 1, plugin, definitionRegistry);
            return;
        }
        // Close button
        if (slot == 49) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 0.8f);
            player.closeInventory();
            return;
        }

        // ── Pet item click ───────────────────────────────────────────────────────
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;

        ItemMeta meta = clicked.getItemMeta();
        NamespacedKey key = new NamespacedKey(plugin, "pet_id");
        if (!meta.getPersistentDataContainer().has(key, PersistentDataType.STRING)) return;

        String petIdStr = meta.getPersistentDataContainer().get(key, PersistentDataType.STRING);
        if (petIdStr == null) return;

        UUID petId;
        try {
            petId = UUID.fromString(petIdStr);
        } catch (IllegalArgumentException e) {
            player.sendMessage(Component.text("Geçersiz pet verisi.", NamedTextColor.RED));
            return;
        }

        // Shift-click → rename
        if (event.isShiftClick()) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.4f);
            player.closeInventory();
            if (sessionManager != null) {
                sessionManager.startRenameSession(player.getUniqueId(), petId);
                player.sendMessage(Component.text(
                        "Lütfen chat ekranına yeni pet ismini yazın ('iptal' yazarak iptal edebilirsiniz):",
                        NamedTextColor.YELLOW));
            }
            return;
        }

        // Left-click → summon / dismiss
        Optional<PetSnapshot> spawned = petService.getSpawnedPet(player.getUniqueId());
        boolean isSpawned = spawned.map(s -> s.petId().equals(petId)).orElse(false);

        if (isSpawned) {
            player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1.1f);
            petService.dismiss(player);
        } else {
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.2f);
            petService.summon(player, petId);
        }
        // Refresh menu on next tick to reflect updated state
        plugin.getServer().getScheduler().runTask(plugin,
                () -> PetListMenu.open(player, petService, holder.page(), plugin, definitionRegistry));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof PetMenuHolder) {
            event.setCancelled(true);
        }
    }
}

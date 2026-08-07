package com.petsistemi.gui;

import com.petsistemi.api.AsyncPetService;
import com.petsistemi.api.PetService;
import com.petsistemi.api.PetSnapshot;
import com.petsistemi.application.PetRuntimeOperationService;
import com.petsistemi.bootstrap.MainThreadDispatcher;
import com.petsistemi.config.RuntimeConfigurationSnapshot;
import com.petsistemi.definition.PetDefinitionRegistry;
import com.petsistemi.message.MessageService;
import com.petsistemi.message.PlaceholderMap;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

public class PetMenuListener implements Listener {

    private static final long CLICK_COOLDOWN_MS = 250L;

    private final JavaPlugin plugin;
    private final PetRuntimeOperationService operationService;
    private final PetService petService;
    private final PlayerInputSessionManager sessionManager;
    private final PetDefinitionRegistry definitionRegistry;
    private final MainThreadDispatcher dispatcher;
    private final MessageService messageService;
    private final AtomicReference<RuntimeConfigurationSnapshot> configSnapshot;

    private final Set<UUID> processingPlayers = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<UUID, Long> lastClickTime = new ConcurrentHashMap<>();

    public PetMenuListener(JavaPlugin plugin,
                           PetRuntimeOperationService operationService,
                           PetService petService,
                           PlayerInputSessionManager sessionManager,
                           PetDefinitionRegistry definitionRegistry,
                           MainThreadDispatcher dispatcher,
                           MessageService messageService,
                           AtomicReference<RuntimeConfigurationSnapshot> configSnapshot) {
        this.plugin = plugin;
        this.operationService = operationService;
        this.petService = petService;
        this.sessionManager = sessionManager;
        this.definitionRegistry = definitionRegistry;
        this.dispatcher = dispatcher;
        this.messageService = messageService;
        this.configSnapshot = configSnapshot;
    }

    public PetMenuListener(JavaPlugin plugin,
                           PetRuntimeOperationService operationService,
                           PetService petService,
                           PlayerInputSessionManager sessionManager,
                           PetDefinitionRegistry definitionRegistry,
                           MainThreadDispatcher dispatcher) {
        this(plugin, operationService, petService, sessionManager, definitionRegistry, dispatcher, null, null);
    }

    public PetMenuListener(JavaPlugin plugin, PetService petService, PlayerInputSessionManager sessionManager, PetDefinitionRegistry definitionRegistry) {
        this(plugin, null, petService, sessionManager, definitionRegistry, null, null, null);
    }

    public PetMenuListener(JavaPlugin plugin, PetService petService, PlayerInputSessionManager sessionManager) {
        this(plugin, null, petService, sessionManager, null, null, null, null);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof PetMenuHolder holder)) return;

        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;

        UUID uuid = player.getUniqueId();

        long now = System.currentTimeMillis();
        Long last = lastClickTime.get(uuid);
        if (last != null && (now - last) < CLICK_COOLDOWN_MS) return;
        if (!processingPlayers.add(uuid)) return;
        lastClickTime.put(uuid, now);

        handleClick(player, holder, event);
    }

    private void handleClick(Player player, PetMenuHolder holder, InventoryClickEvent event) {
        UUID uuid = player.getUniqueId();
        int slot = event.getRawSlot();

        if (slot == 45 && holder.page() > 0) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
            PetListMenu.openAsync(player, petService, holder.page() - 1, plugin, definitionRegistry, dispatcher, configSnapshot, messageService);
            processingPlayers.remove(uuid);
            return;
        }

        if (slot == 53) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
            PetListMenu.openAsync(player, petService, holder.page() + 1, plugin, definitionRegistry, dispatcher, configSnapshot, messageService);
            processingPlayers.remove(uuid);
            return;
        }

        if (slot == 49) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 0.8f);
            player.closeInventory();
            processingPlayers.remove(uuid);
            return;
        }

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) {
            processingPlayers.remove(uuid);
            return;
        }

        ItemMeta meta = clicked.getItemMeta();
        NamespacedKey key = new NamespacedKey(plugin, "pet_id");
        NamespacedKey actionKey = new NamespacedKey(plugin, "action");

        if (!meta.getPersistentDataContainer().has(key, PersistentDataType.STRING)) {
            processingPlayers.remove(uuid);
            return;
        }

        String petIdStr = meta.getPersistentDataContainer().get(key, PersistentDataType.STRING);
        String actionStr = meta.getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);

        if (petIdStr == null) {
            processingPlayers.remove(uuid);
            return;
        }

        UUID petId;
        try {
            petId = UUID.fromString(petIdStr);
        } catch (IllegalArgumentException e) {
            send(player, "command.invalid-pet-data", "<red>Geçersiz pet verisi.</red>");
            processingPlayers.remove(uuid);
            return;
        }

        // Handle Inspect Menu Specific Actions
        if ("PET_INSPECT".equals(holder.menuType()) && actionStr != null) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
            switch (actionStr) {
                case "rename" -> {
                    player.closeInventory();
                    if (sessionManager != null) {
                        sessionManager.startRenameSession(player.getUniqueId(), petId);
                        send(player, "command.rename-prompt", "<yellow>Lütfen chat ekranına yeni pet ismini yazın ('iptal' yazarak iptal edebilirsiniz):</yellow>");
                    }
                }
                case "mode" -> {
                    player.closeInventory();
                    player.performCommand("pet mode");
                }
                case "emote" -> {
                    com.petsistemi.domain.PetDefinition def = null;
                    if (definitionRegistry != null) {
                        def = definitionRegistry.getAll().stream().findFirst().orElse(null);
                    }
                    if (def != null && def.emotes() != null && !def.emotes().isEmpty()) {
                        PetEmoteMenu.open(player, petId, plugin, definitionRegistry, configSnapshot, messageService, def);
                    } else {
                        player.closeInventory();
                        player.performCommand("pet emote happy");
                    }
                }
            }
            processingPlayers.remove(uuid);
            return;
        }

        if ("PET_EMOTE".equals(holder.menuType())) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
            NamespacedKey emoteKey = new NamespacedKey(plugin, "emote_name");
            String emoteName = meta.getPersistentDataContainer().get(emoteKey, PersistentDataType.STRING);
            if (emoteName != null) {
                player.closeInventory();
                player.performCommand("pet emote " + emoteName);
            }
            processingPlayers.remove(uuid);
            return;
        }

        if ("PET_LIST_ALL".equals(holder.menuType())) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
            if (event.isRightClick()) {
                PetInspectMenu.open(player, petService, petId, player.getUniqueId(), plugin, definitionRegistry, configSnapshot, messageService);
            } else {
                String shortId = petId.toString().substring(0, 6);
                player.closeInventory();
                player.performCommand("pet summon " + shortId);
            }
            processingPlayers.remove(uuid);
            return;
        }

        if (event.isShiftClick()) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.4f);
            player.closeInventory();
            if (sessionManager != null) {
                sessionManager.startRenameSession(player.getUniqueId(), petId);
                send(player, "command.rename-prompt",
                        "<yellow>Lütfen chat ekranına yeni pet ismini yazın ('iptal' yazarak iptal edebilirsiniz):</yellow>");
            }
            processingPlayers.remove(uuid);
            return;
        }

        CompletableFuture<Optional<PetSnapshot>> selectedFuture = petService instanceof AsyncPetService async ? async.getSelectedPetAsync(uuid) : CompletableFuture.completedFuture(petService.getSelectedPet(uuid));

        selectedFuture.thenAccept(selectedOpt -> {
            boolean isSpawned = selectedOpt.isPresent() && selectedOpt.get().petId().equals(petId) && selectedOpt.get().spawned();

            if (isSpawned) {
                CompletableFuture<?> opFuture = operationService != null ? operationService.dismissAsync(player) : CompletableFuture.completedFuture(petService.dismiss(player));
                handleOperationResult(player, holder, uuid, opFuture, "command.dismiss-failed");
            } else {
                CompletableFuture<?> opFuture = operationService != null ? operationService.summonAsync(player, petId) : CompletableFuture.completedFuture(petService.summon(player, petId));
                handleOperationResult(player, holder, uuid, opFuture, "command.summon-failed");
            }
        }).exceptionally(ex -> {
            send(player, "command.invalid-pet-data", "<red>Pet durumu alınamadı: " + ex.getMessage() + "</red>");
            processingPlayers.remove(uuid);
            return null;
        });
    }

    private void send(Player player, String key, String fallback) {
        if (messageService != null) {
            messageService.send(player, key, fallback);
        } else if (player != null) {
            player.sendMessage(Component.text(fallback, NamedTextColor.RED));
        }
    }

    private void handleOperationResult(Player player, PetMenuHolder holder, UUID uuid,
                                       CompletableFuture<?> opFuture, String errorKey) {
        opFuture.whenComplete((res, ex) -> {
            boolean success = ex == null
                    && ((res instanceof com.petsistemi.api.result.PetSummonResult sr && sr.success())
                    || (res instanceof com.petsistemi.api.result.PetDismissResult dr && dr.success())
                    || (!(res instanceof com.petsistemi.api.result.PetSummonResult) && !(res instanceof com.petsistemi.api.result.PetDismissResult)));

            String errorMessage = null;
            if (ex != null) {
                errorMessage = ex.getMessage();
            } else if (res instanceof com.petsistemi.api.result.PetSummonResult sr && !sr.success()) {
                errorMessage = sr.message();
            } else if (res instanceof com.petsistemi.api.result.PetDismissResult dr && !dr.success()) {
                errorMessage = dr.message();
            }

            final String message = errorMessage;
            Runnable refreshAction = () -> {
                try {
                    if (player.isOnline()) {
                        if (message != null) {
                            sendError(player, errorKey, message);
                        } else {
                            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.2f);
                            PetListMenu.openAsync(player, petService, holder.page(), plugin, definitionRegistry, dispatcher, configSnapshot, messageService);
                        }
                    }
                } finally {
                    processingPlayers.remove(uuid);
                }
            };
            if (dispatcher != null) dispatcher.run(refreshAction);
            else refreshAction.run();
        });
    }

    private void sendError(Player player, String key, String error) {
        if (messageService != null) {
            messageService.send(player, key, "<red>" + error + "</red>", PlaceholderMap.of("error", error));
        } else if (player != null) {
            player.sendMessage(Component.text(error, NamedTextColor.RED));
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof PetMenuHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        lastClickTime.remove(uuid);
        processingPlayers.remove(uuid);
    }

    @EventHandler
    public void onClose(org.bukkit.event.inventory.InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof PetMenuHolder) {
            UUID uuid = event.getPlayer().getUniqueId();
            processingPlayers.remove(uuid);
        }
    }
}

package com.petsistemi.listener;

import com.petsistemi.api.PetService;
import com.petsistemi.config.RuntimeConfigurationSnapshot;
import com.petsistemi.definition.PetDefinitionRegistry;
import com.petsistemi.domain.PetDefinition;
import com.petsistemi.domain.PetMountDefinition;
import com.petsistemi.gui.PetInspectMenu;
import com.petsistemi.message.MessageService;
import com.petsistemi.message.PlaceholderMap;
import com.petsistemi.runtime.ActivePet;
import com.petsistemi.runtime.ActivePetRegistry;
import com.petsistemi.runtime.InteractionHitboxController;
import com.petsistemi.runtime.item.PetItemActionEngine;
import com.petsistemi.runtime.item.PetItemActionOutcome;
import com.petsistemi.runtime.item.PetItemActionStatus;
import com.petsistemi.runtime.mount.PetMountController;
import com.petsistemi.api.mount.PetMountResult;
import com.petsistemi.api.mount.PetMountStatus;
import org.bukkit.GameMode;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityTameEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.entity.PlayerLeashEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public class PetProtectionListener implements Listener {

    private final ActivePetRegistry activeRegistry;
    private final PetService petService;
    private final JavaPlugin plugin;
    private final PetDefinitionRegistry definitionRegistry;
    private final AtomicReference<RuntimeConfigurationSnapshot> configSnapshot;
    private final MessageService messageService;
    private final InteractionHitboxController hitboxController;
    private final PetItemActionEngine itemActionEngine;
    private final PetMountController mountController;

    public PetProtectionListener(ActivePetRegistry activeRegistry) {
        this(activeRegistry, null, null, null, null, null, null, null, null);
    }

    public PetProtectionListener(ActivePetRegistry activeRegistry, PetService petService,
                                 JavaPlugin plugin, PetDefinitionRegistry definitionRegistry) {
        this(activeRegistry, petService, plugin, definitionRegistry, null, null, null, null, null);
    }

    public PetProtectionListener(ActivePetRegistry activeRegistry, PetService petService,
                                 JavaPlugin plugin, PetDefinitionRegistry definitionRegistry,
                                 AtomicReference<RuntimeConfigurationSnapshot> configSnapshot,
                                 MessageService messageService) {
        this(activeRegistry, petService, plugin, definitionRegistry, configSnapshot, messageService, null, null, null);
    }

    public PetProtectionListener(ActivePetRegistry activeRegistry, PetService petService,
                                 JavaPlugin plugin, PetDefinitionRegistry definitionRegistry,
                                 AtomicReference<RuntimeConfigurationSnapshot> configSnapshot,
                                 MessageService messageService,
                                 InteractionHitboxController hitboxController) {
        this(activeRegistry, petService, plugin, definitionRegistry, configSnapshot, messageService,
                hitboxController, null, null);
    }

    public PetProtectionListener(ActivePetRegistry activeRegistry, PetService petService,
                                 JavaPlugin plugin, PetDefinitionRegistry definitionRegistry,
                                 AtomicReference<RuntimeConfigurationSnapshot> configSnapshot,
                                 MessageService messageService,
                                 InteractionHitboxController hitboxController,
                                 PetItemActionEngine itemActionEngine) {
        this(activeRegistry, petService, plugin, definitionRegistry, configSnapshot, messageService,
                hitboxController, itemActionEngine, null);
    }

    public PetProtectionListener(ActivePetRegistry activeRegistry, PetService petService,
                                 JavaPlugin plugin, PetDefinitionRegistry definitionRegistry,
                                 AtomicReference<RuntimeConfigurationSnapshot> configSnapshot,
                                 MessageService messageService,
                                 InteractionHitboxController hitboxController,
                                 PetItemActionEngine itemActionEngine,
                                 PetMountController mountController) {
        this.activeRegistry = activeRegistry;
        this.petService = petService;
        this.plugin = plugin;
        this.definitionRegistry = definitionRegistry;
        this.configSnapshot = configSnapshot;
        this.messageService = messageService;
        this.hitboxController = hitboxController;
        this.itemActionEngine = itemActionEngine;
        this.mountController = mountController;
    }

    @EventHandler
    public void onTame(EntityTameEvent event) {
        if (activeRegistry.getByAnyEntity(event.getEntity().getUniqueId()).isPresent()) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onLeash(PlayerLeashEntityEvent event) {
        if (activeRegistry.getByAnyEntity(event.getEntity().getUniqueId()).isPresent()) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBreed(EntityBreedEvent event) {
        boolean parent1IsPet = activeRegistry.getByAnyEntity(event.getFather().getUniqueId()).isPresent();
        boolean parent2IsPet = activeRegistry.getByAnyEntity(event.getMother().getUniqueId()).isPresent();
        if (parent1IsPet || parent2IsPet) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.LOWEST)
    public void onDamage(org.bukkit.event.entity.EntityDamageEvent event) {
        if (activeRegistry.getByAnyEntity(event.getEntity().getUniqueId()).isPresent()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.LOWEST)
    public void onDamageByEntity(org.bukkit.event.entity.EntityDamageByEntityEvent event) {
        if (activeRegistry.getByAnyEntity(event.getEntity().getUniqueId()).isPresent()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.LOWEST)
    public void onCombust(org.bukkit.event.entity.EntityCombustEvent event) {
        if (activeRegistry.getByAnyEntity(event.getEntity().getUniqueId()).isPresent()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.LOWEST)
    public void onProjectileHit(org.bukkit.event.entity.ProjectileHitEvent event) {
        if (event.getHitEntity() != null && activeRegistry.getByAnyEntity(event.getHitEntity().getUniqueId()).isPresent()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.MONITOR, ignoreCancelled = true)
    public void onBedEnter(org.bukkit.event.player.PlayerBedEnterEvent event) {
        activeRegistry.getByOwner(event.getPlayer().getUniqueId()).ifPresent(active -> {
            Entity entity = active.getSpawnedEntity();
            if (entity != null && entity.isValid()) {
                if (entity instanceof org.bukkit.entity.Sittable sittable) {
                    sittable.setSitting(true);
                }
                try {
                    entity.getWorld().spawnParticle(org.bukkit.Particle.VILLAGER_HAPPY, entity.getLocation().add(0, 0.5, 0), 5, 0.2, 0.2, 0.2, 0.02);
                } catch (Exception ignored) {}
            }
        });
    }

    @EventHandler
    public void onBedLeave(org.bukkit.event.player.PlayerBedLeaveEvent event) {
        activeRegistry.getByOwner(event.getPlayer().getUniqueId()).ifPresent(active -> {
            Entity entity = active.getSpawnedEntity();
            if (entity != null && entity.isValid() && entity instanceof org.bukkit.entity.Sittable sittable) {
                sittable.setSitting(false);
            }
        });
    }

    /**
     * Single entry point for right-clicks on anything belonging to a pet: the pet body,
     * a tracked child (e.g. a swarm member), or an invisible interaction hitbox.
     *
     * <p>Kept as one handler on purpose. Two listeners on this event raced each other —
     * whichever cancelled first silently suppressed the other, so behaviour depended on
     * registration order.</p>
     */
    @EventHandler
    public void onInteract(PlayerInteractEntityEvent event) {
        Optional<ActivePet> activeOpt = resolvePet(event.getRightClicked().getUniqueId());
        if (activeOpt.isEmpty()) {
            return;
        }
        event.setCancelled(true);

        Player player = event.getPlayer();
        ActivePet activePet = activeOpt.get();
        boolean isOwner = activePet.getOwnerId().equals(player.getUniqueId());

        if (isOwner && event.getHand() == EquipmentSlot.HAND && tryItemAction(player, activePet)) {
            return;
        }
        // Bukkit fires the interaction for both hands. Mount/inspect must run once only;
        // otherwise the off-hand event immediately toggles a successful mount back off.
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        if (player.isSneaking() && isOwner && (mountController != null || canRide(player, activePet))) {
            toggleRide(player, activePet, mountTarget(activePet, event.getRightClicked()));
        } else if (petService != null) {
            PetInspectMenu.open(player, petService, activePet.getPetId(), activePet.getOwnerId(), plugin, definitionRegistry, configSnapshot, messageService);
        }
    }

    private boolean tryItemAction(Player player, ActivePet activePet) {
        if (itemActionEngine == null || definitionRegistry == null) return false;
        PetDefinition definition = definitionRegistry.find(activePet.getDefinitionId()).orElse(null);
        ItemStack held = player.getInventory().getItemInMainHand();
        if (definition == null || !itemActionEngine.matches(definition, held)) return false;

        ItemStack paid = held.clone();
        java.util.concurrent.CompletableFuture<PetItemActionOutcome> future =
                itemActionEngine.use(player, activePet, definition, held);
        PetItemActionOutcome immediate = future.getNow(null);
        if (immediate != null) {
            if (immediate.success()) consume(player, immediate.definition().consumeAmount());
            sendItemOutcome(player, immediate);
            return true;
        }

        int consumeAmount = definition.itemActions().stream()
                .filter(action -> action.material().equalsIgnoreCase(held.getType().name()))
                .filter(action -> action.customModelData() == null || (held.hasItemMeta()
                        && held.getItemMeta().hasCustomModelData()
                        && held.getItemMeta().getCustomModelData() == action.customModelData()))
                .findFirst().map(com.petsistemi.domain.item.PetItemActionDefinition::consumeAmount).orElse(0);
        paid.setAmount(consumeAmount);
        consume(player, consumeAmount);
        future.whenComplete((outcome, error) -> runOnMainThread(() -> {
            PetItemActionOutcome resolved = outcome;
            if (resolved == null) {
                resolved = new PetItemActionOutcome(PetItemActionStatus.FAILED,
                        error != null ? error.getMessage() : "Item işlemi başarısız.", null, 0);
            }
            if (!resolved.success()) refund(player, paid);
            sendItemOutcome(player, resolved);
        }));
        return true;
    }

    private static void consume(Player player, int amount) {
        if (amount <= 0 || player.getGameMode() == GameMode.CREATIVE) return;
        ItemStack current = player.getInventory().getItemInMainHand();
        int remaining = current.getAmount() - amount;
        if (remaining <= 0) player.getInventory().setItemInMainHand(null);
        else current.setAmount(remaining);
    }

    private static void refund(Player player, ItemStack paid) {
        if (paid == null || paid.getAmount() <= 0 || player.getGameMode() == GameMode.CREATIVE) return;
        player.getInventory().addItem(paid).values().forEach(leftover ->
                player.getWorld().dropItemNaturally(player.getLocation(), leftover));
    }

    private void sendItemOutcome(Player player, PetItemActionOutcome outcome) {
        if (outcome == null || outcome.status() == PetItemActionStatus.NOT_MATCHED) return;
        String message = outcome.message() != null ? outcome.message() : "Item işlemi tamamlanamadı.";
        if (outcome.status() == PetItemActionStatus.COOLDOWN) {
            message += " Kalan süre: " + outcome.remainingSeconds() + " saniye.";
        }
        net.kyori.adventure.text.format.NamedTextColor color = outcome.success()
                ? net.kyori.adventure.text.format.NamedTextColor.GREEN
                : net.kyori.adventure.text.format.NamedTextColor.RED;
        player.sendMessage(net.kyori.adventure.text.Component.text(message, color));
    }

    private void runOnMainThread(Runnable action) {
        if (plugin != null && plugin.getServer() != null) plugin.getServer().getScheduler().runTask(plugin, action);
        else action.run();
    }

    @EventHandler
    public void onQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        if (itemActionEngine != null) itemActionEngine.cleanup(event.getPlayer().getUniqueId());
        if (mountController != null) mountController.cleanup(event.getPlayer().getUniqueId());
    }

    /** Pet body / tracked child first, then the interaction hitbox registry. */
    private Optional<ActivePet> resolvePet(java.util.UUID clickedEntityId) {
        Optional<ActivePet> direct = activeRegistry.getByAnyEntity(clickedEntityId);
        if (direct.isPresent() || hitboxController == null) {
            return direct;
        }
        return activeRegistry.getByPetId(hitboxController.getPetIdFromHitbox(clickedEntityId));
    }

    /**
     * A hitbox is not rideable — it is a click target that follows the pet. Mounting must
     * always target the pet's real body.
     */
    private Entity mountTarget(ActivePet activePet, Entity clicked) {
        Entity body = activePet.getSpawnedEntity();
        return (body != null && body.isValid()) ? body : clicked;
    }

    /**
     * Riding is gated by the global feature flag, and additionally by the definition's own
     * {@code mount:} block when it declares one.
     */
    private boolean canRide(Player player, ActivePet activePet) {
        PetDefinition definition = definitionRegistry != null && activePet.getDefinitionId() != null
                ? definitionRegistry.find(activePet.getDefinitionId()).orElse(null)
                : null;
        PetMountDefinition mount = definition != null ? definition.mount() : null;

        if (mount != null) {
            if (!mount.enabled()) {
                return false;
            }
            if (mount.permission() != null && !player.hasPermission(mount.permission())) {
                sendMessage(player, "riding.no-permission",
                        "<red>Bu pete binme yetkiniz yok.</red>", petName(activePet));
                return false;
            }
            return true;
        }
        return isRidingEnabled();
    }

    private boolean isRidingEnabled() {
        return configSnapshot != null
                && configSnapshot.get() != null
                && configSnapshot.get().configuration() != null
                && configSnapshot.get().configuration().features() != null
                && configSnapshot.get().configuration().features().ridingEnabled();
    }

    private void toggleRide(Player player, ActivePet activePet, Entity petEntity) {
        String petName = petName(activePet);
        if (mountController != null) {
            PetMountResult result = mountController.toggleMount(player);
            String key;
            String fallback;
            if (result.status() == PetMountStatus.MOUNTED) {
                key = "riding.enter";
                fallback = "<green><name> üzerine bindiniz! WASD ile yönetin, Space ile zıplayın.</green>";
            } else if (result.status() == PetMountStatus.DISMOUNTED) {
                key = "riding.exit";
                fallback = "<yellow><name> üzerinden indiniz.</yellow>";
            } else if (result.status() == PetMountStatus.NO_PERMISSION) {
                key = "riding.no-permission";
                fallback = "<red><name> petine binme yetkiniz yok.</red>";
            } else if (result.status() == PetMountStatus.DISABLED) {
                key = "riding.disabled";
                fallback = "<red>Bu pet için sürüş devre dışı.</red>";
            } else {
                key = "riding.failed";
                fallback = "<red>Pet sürüşü başlatılamadı: " + result.message() + "</red>";
            }
            sendMessage(player, key, fallback, petName);
            return;
        }
        if (player.getVehicle() == petEntity) {
            player.leaveVehicle();
            sendMessage(player, "riding.exit", "<yellow><name> üzerinden indiniz.</yellow>", petName);
        } else {
            petEntity.addPassenger(player);
            sendMessage(player, "riding.enter", "<green><name> üzerine bindiniz! İnmek için tekrar Shift + sağ tık yapın.</green>", petName);
        }
    }

    private String petName(ActivePet activePet) {
        if (definitionRegistry == null || activePet.getDefinitionId() == null) {
            return activePet.getDefinitionId() != null ? activePet.getDefinitionId() : "pet";
        }
        return definitionRegistry.find(activePet.getDefinitionId())
                .map(d -> d.displayName())
                .orElse(activePet.getDefinitionId());
    }

    private void sendMessage(Player player, String key, String fallback, String petName) {
        if (messageService != null) {
            messageService.send(player, key, fallback, PlaceholderMap.of("name", petName));
        } else if (player != null) {
            player.sendMessage(com.petsistemi.message.MiniMessageRenderer.render(fallback, PlaceholderMap.of("name", petName)));
        }
    }
}

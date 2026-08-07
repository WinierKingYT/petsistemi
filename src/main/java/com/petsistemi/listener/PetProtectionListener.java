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
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityTameEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.entity.PlayerLeashEntityEvent;
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

    public PetProtectionListener(ActivePetRegistry activeRegistry) {
        this(activeRegistry, null, null, null, null, null);
    }

    public PetProtectionListener(ActivePetRegistry activeRegistry, PetService petService,
                                 JavaPlugin plugin, PetDefinitionRegistry definitionRegistry) {
        this(activeRegistry, petService, plugin, definitionRegistry, null, null);
    }

    public PetProtectionListener(ActivePetRegistry activeRegistry, PetService petService,
                                 JavaPlugin plugin, PetDefinitionRegistry definitionRegistry,
                                 AtomicReference<RuntimeConfigurationSnapshot> configSnapshot,
                                 MessageService messageService) {
        this(activeRegistry, petService, plugin, definitionRegistry, configSnapshot, messageService, null);
    }

    public PetProtectionListener(ActivePetRegistry activeRegistry, PetService petService,
                                 JavaPlugin plugin, PetDefinitionRegistry definitionRegistry,
                                 AtomicReference<RuntimeConfigurationSnapshot> configSnapshot,
                                 MessageService messageService,
                                 InteractionHitboxController hitboxController) {
        this.activeRegistry = activeRegistry;
        this.petService = petService;
        this.plugin = plugin;
        this.definitionRegistry = definitionRegistry;
        this.configSnapshot = configSnapshot;
        this.messageService = messageService;
        this.hitboxController = hitboxController;
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

        if (player.isSneaking() && isOwner && canRide(player, activePet)) {
            toggleRide(player, activePet, mountTarget(activePet, event.getRightClicked()));
        } else if (petService != null) {
            PetInspectMenu.open(player, petService, activePet.getPetId(), activePet.getOwnerId(), plugin, definitionRegistry, configSnapshot, messageService);
        }
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

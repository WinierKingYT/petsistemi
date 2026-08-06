package com.petsistemi.listener;

import com.petsistemi.api.PetService;
import com.petsistemi.config.RuntimeConfigurationSnapshot;
import com.petsistemi.definition.PetDefinitionRegistry;
import com.petsistemi.gui.PetInspectMenu;
import com.petsistemi.message.MessageService;
import com.petsistemi.message.PlaceholderMap;
import com.petsistemi.runtime.ActivePet;
import com.petsistemi.runtime.ActivePetRegistry;
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
        this.activeRegistry = activeRegistry;
        this.petService = petService;
        this.plugin = plugin;
        this.definitionRegistry = definitionRegistry;
        this.configSnapshot = configSnapshot;
        this.messageService = messageService;
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

    @EventHandler
    public void onInteract(PlayerInteractEntityEvent event) {
        Optional<ActivePet> activeOpt = activeRegistry.getByAnyEntity(event.getRightClicked().getUniqueId());
        if (activeOpt.isEmpty()) {
            return;
        }
        event.setCancelled(true);

        Player player = event.getPlayer();
        ActivePet activePet = activeOpt.get();
        boolean isOwner = activePet.getOwnerId().equals(player.getUniqueId());

        if (player.isSneaking() && isOwner && isRidingEnabled()) {
            toggleRide(player, activePet, event.getRightClicked());
        } else if (petService != null) {
            PetInspectMenu.open(player, petService, activePet.getPetId(), activePet.getOwnerId(), plugin, definitionRegistry, configSnapshot, messageService);
        }
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

package com.petsistemi.listener;

import com.petsistemi.runtime.ActivePetRegistry;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityTameEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.entity.PlayerLeashEntityEvent;

public class PetProtectionListener implements Listener {

    private final ActivePetRegistry activeRegistry;

    public PetProtectionListener(ActivePetRegistry activeRegistry) {
        this.activeRegistry = activeRegistry;
    }

    @EventHandler
    public void onTame(EntityTameEvent event) {
        if (activeRegistry.getByEntity(event.getEntity().getUniqueId()).isPresent()) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onLeash(PlayerLeashEntityEvent event) {
        if (activeRegistry.getByEntity(event.getEntity().getUniqueId()).isPresent()) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBreed(EntityBreedEvent event) {
        boolean parent1IsPet = activeRegistry.getByEntity(event.getFather().getUniqueId()).isPresent();
        boolean parent2IsPet = activeRegistry.getByEntity(event.getMother().getUniqueId()).isPresent();
        if (parent1IsPet || parent2IsPet) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEntityEvent event) {
        if (activeRegistry.getByEntity(event.getRightClicked().getUniqueId()).isPresent()) {
            event.setCancelled(true);
        }
    }
}

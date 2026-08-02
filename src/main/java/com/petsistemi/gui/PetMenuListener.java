package com.petsistemi.gui;

import com.petsistemi.api.PetService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

public class PetMenuListener implements Listener {

    private final PetService petService;

    public PetMenuListener(PetService petService) {
        this.petService = petService;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() instanceof PetMenuHolder holder) {
            event.setCancelled(true);

            if (!(event.getWhoClicked() instanceof Player player)) {
                return;
            }

            int slot = event.getRawSlot();
            if (slot == 45 && holder.page() > 0) {
                PetListMenu.open(player, petService, holder.page() - 1);
            } else if (slot == 53) {
                PetListMenu.open(player, petService, holder.page() + 1);
            } else if (slot == 49) {
                player.closeInventory();
            }
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof PetMenuHolder) {
            event.setCancelled(true);
        }
    }
}

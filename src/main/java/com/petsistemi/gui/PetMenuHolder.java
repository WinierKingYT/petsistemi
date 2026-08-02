package com.petsistemi.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public record PetMenuHolder(String menuType, int page) implements InventoryHolder {
    @Override
    public Inventory getInventory() {
        return null;
    }
}

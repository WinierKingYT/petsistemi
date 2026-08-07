package com.petsistemi.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public record PetMenuHolder(String menuType, int page) implements InventoryHolder {

    public PetMenuHolder {
        page = Math.max(0, page);
        menuType = menuType != null ? menuType : "DEFAULT";
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}

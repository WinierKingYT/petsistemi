package com.petsistemi.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public record PetMenuHolder(String menuType, int page, String context) implements InventoryHolder {

    public PetMenuHolder {
        page = Math.max(0, page);
        menuType = menuType != null ? menuType : "DEFAULT";
        context = context != null ? context : "";
    }

    public PetMenuHolder(String menuType, int page) {
        this(menuType, page, "");
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}

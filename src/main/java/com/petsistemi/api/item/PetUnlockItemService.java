package com.petsistemi.api.item;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.concurrent.CompletableFuture;

/** Creates and redeems pet unlock items that do not require an active pet. */
public interface PetUnlockItemService {
    ItemStack create(String definitionId, int amount, Material material);
    boolean matches(ItemStack item);
    CompletableFuture<PetItemActionResult> redeem(Player player, ItemStack item);
}

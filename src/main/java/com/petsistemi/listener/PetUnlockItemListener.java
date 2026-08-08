package com.petsistemi.listener;

import com.petsistemi.api.item.PetItemActionResult;
import com.petsistemi.runtime.item.PetUnlockItemController;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public final class PetUnlockItemListener implements Listener {

    private final JavaPlugin plugin;
    private final PetUnlockItemController controller;

    public PetUnlockItemListener(JavaPlugin plugin, PetUnlockItemController controller) {
        this.plugin = plugin;
        this.controller = controller;
    }

    @EventHandler
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)) return;
        Player player = event.getPlayer();
        ItemStack held = player.getInventory().getItemInMainHand();
        if (!controller.matches(held)) return;
        event.setCancelled(true);

        ItemStack refund = held.clone();
        refund.setAmount(1);
        var future = controller.redeem(player, held);
        PetItemActionResult immediate = future.getNow(null);
        if (immediate != null) {
            if (immediate.success()) consumeOne(player);
            send(player, immediate);
            return;
        }

        consumeOne(player);
        future.whenComplete((result, error) -> runOnMain(() -> {
            PetItemActionResult resolved = result != null ? result
                    : PetItemActionResult.failure(error != null ? error.getMessage() : "Unlock işlemi başarısız.");
            if (!resolved.success()) refund(player, refund);
            send(player, resolved);
        }));
    }

    private static void consumeOne(Player player) {
        if (player.getGameMode() == GameMode.CREATIVE) return;
        ItemStack current = player.getInventory().getItemInMainHand();
        if (current.getAmount() <= 1) player.getInventory().setItemInMainHand(null);
        else current.setAmount(current.getAmount() - 1);
    }

    private static void refund(Player player, ItemStack item) {
        if (player.getGameMode() == GameMode.CREATIVE) return;
        player.getInventory().addItem(item).values().forEach(leftover ->
                player.getWorld().dropItemNaturally(player.getLocation(), leftover));
    }

    private static void send(Player player, PetItemActionResult result) {
        player.sendMessage(Component.text(result.message(), result.success() ? NamedTextColor.GREEN : NamedTextColor.RED));
    }

    private void runOnMain(Runnable action) {
        if (plugin.getServer().isPrimaryThread()) action.run();
        else plugin.getServer().getScheduler().runTask(plugin, action);
    }
}

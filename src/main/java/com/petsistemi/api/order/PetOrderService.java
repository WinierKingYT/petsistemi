package com.petsistemi.api.order;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;

import java.util.Set;
import java.util.concurrent.CompletionStage;

/**
 * Bukkit service extension point for registering and issuing pet orders.
 *
 * <p>{@link #executeOrder(Player, NamespacedKey)} must be called on the Bukkit main thread.
 * A handler may return an asynchronous stage, but must schedule any later Bukkit API work
 * back onto the main thread itself.</p>
 */
public interface PetOrderService {
    void registerOrder(NamespacedKey key, PetOrderHandler handler);
    void unregisterOrder(NamespacedKey key);
    Set<NamespacedKey> registeredOrders();
    CompletionStage<PetOrderResult> executeOrder(Player player, NamespacedKey key);
}

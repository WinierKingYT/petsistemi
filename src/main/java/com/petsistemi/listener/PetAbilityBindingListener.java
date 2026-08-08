package com.petsistemi.listener;

import com.petsistemi.runtime.ability.AbilityOutcome;
import com.petsistemi.runtime.ability.PetAbilityBindingController;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;

/** Activates the explicitly bound ability on sneak + swap-hand. */
public final class PetAbilityBindingListener implements Listener {
    private final PetAbilityBindingController bindings;

    public PetAbilityBindingListener(PetAbilityBindingController bindings) { this.bindings = bindings; }

    @EventHandler(ignoreCancelled = true)
    public void onSwapHand(PlayerSwapHandItemsEvent event) {
        if (bindings == null || !event.getPlayer().isSneaking()
                || bindings.binding(event.getPlayer().getUniqueId()).isEmpty()) return;
        event.setCancelled(true);
        AbilityOutcome outcome = bindings.activateBound(event.getPlayer());
        switch (outcome.result()) {
            case ACTIVATED -> event.getPlayer().sendActionBar(net.kyori.adventure.text.Component.text("Ability kullanıldı!"));
            case COOLDOWN -> event.getPlayer().sendActionBar(net.kyori.adventure.text.Component.text(
                    "Cooldown: " + outcome.remainingSeconds() + " sn"));
            case NO_TARGET -> event.getPlayer().sendActionBar(net.kyori.adventure.text.Component.text("Uygun hedef yok."));
            default -> event.getPlayer().sendActionBar(net.kyori.adventure.text.Component.text("Ability kullanılamadı."));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (bindings != null) bindings.unbind(event.getPlayer().getUniqueId());
    }
}

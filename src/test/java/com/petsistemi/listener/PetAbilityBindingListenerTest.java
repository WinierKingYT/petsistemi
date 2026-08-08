package com.petsistemi.listener;

import com.petsistemi.runtime.ability.AbilityOutcome;
import com.petsistemi.runtime.ability.AbilityResult;
import com.petsistemi.runtime.ability.PetAbilityBindingController;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.*;

class PetAbilityBindingListenerTest {
    @Test
    void sneakSwapIsCancelledAndActivatesExplicitBinding() {
        PetAbilityBindingController bindings = mock(PetAbilityBindingController.class);
        Player player = mock(Player.class);
        UUID ownerId = UUID.randomUUID();
        NamespacedKey key = new NamespacedKey("petsistemi", "pulse");
        when(player.getUniqueId()).thenReturn(ownerId);
        when(player.isSneaking()).thenReturn(true);
        when(bindings.binding(ownerId)).thenReturn(Optional.of(key));
        when(bindings.activateBound(player)).thenReturn(new AbilityOutcome(AbilityResult.ACTIVATED, key, 0, 1));
        PlayerSwapHandItemsEvent event = mock(PlayerSwapHandItemsEvent.class);
        when(event.getPlayer()).thenReturn(player);

        new PetAbilityBindingListener(bindings).onSwapHand(event);

        verify(event).setCancelled(true);
        verify(bindings).activateBound(player);
    }

    @Test
    void normalSwapWithoutSneakOrBindingIsUntouched() {
        PetAbilityBindingController bindings = mock(PetAbilityBindingController.class);
        Player player = mock(Player.class);
        UUID ownerId = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(ownerId);
        when(player.isSneaking()).thenReturn(false);
        PlayerSwapHandItemsEvent event = mock(PlayerSwapHandItemsEvent.class);
        when(event.getPlayer()).thenReturn(player);

        new PetAbilityBindingListener(bindings).onSwapHand(event);

        verify(event, never()).setCancelled(true);
        verify(bindings, never()).activateBound(any());
    }
}

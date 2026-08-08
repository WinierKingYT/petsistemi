package com.petsistemi.listener;

import com.petsistemi.api.mount.PetMountResult;
import com.petsistemi.api.mount.PetMountStatus;
import com.petsistemi.domain.PetRuntimeState;
import com.petsistemi.runtime.ActivePet;
import com.petsistemi.runtime.ActivePetRegistry;
import com.petsistemi.runtime.mount.PetMountController;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.mockito.Mockito.*;

class PetMountInteractionTest {

    @Test
    void offHandEventDoesNotImmediatelyToggleMountBackOff() {
        UUID ownerId = UUID.randomUUID();
        Entity entity = mock(Entity.class);
        UUID entityId = UUID.randomUUID();
        when(entity.getUniqueId()).thenReturn(entityId);
        ActivePetRegistry registry = new ActivePetRegistry();
        registry.register(new ActivePet(UUID.randomUUID(), ownerId, "wolf", 1,
                entityId, entity, PetRuntimeState.ACTIVE));
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(ownerId);
        when(player.isSneaking()).thenReturn(true);
        PetMountController mounts = mock(PetMountController.class);
        when(mounts.toggleMount(player)).thenReturn(
                new PetMountResult(PetMountStatus.MOUNTED, "ok"));
        PetProtectionListener listener = new PetProtectionListener(
                registry, null, null, null, null, null, null, null, mounts);
        PlayerInteractEntityEvent offHand = mock(PlayerInteractEntityEvent.class);
        when(offHand.getRightClicked()).thenReturn(entity);
        when(offHand.getPlayer()).thenReturn(player);
        when(offHand.getHand()).thenReturn(EquipmentSlot.OFF_HAND);
        PlayerInteractEntityEvent mainHand = mock(PlayerInteractEntityEvent.class);
        when(mainHand.getRightClicked()).thenReturn(entity);
        when(mainHand.getPlayer()).thenReturn(player);
        when(mainHand.getHand()).thenReturn(EquipmentSlot.HAND);

        listener.onInteract(mainHand);
        listener.onInteract(offHand);

        verify(mounts, times(1)).toggleMount(player);
    }
}

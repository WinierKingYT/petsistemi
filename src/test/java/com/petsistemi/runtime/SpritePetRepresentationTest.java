package com.petsistemi.runtime;

import be.seeseemelk.mockbukkit.MockBukkit;
import com.petsistemi.domain.PetAvailabilityState;
import com.petsistemi.domain.PetDefinition;
import com.petsistemi.domain.PetInstance;
import com.petsistemi.domain.PetRepresentationDefinition;
import com.petsistemi.domain.PetVector3;
import com.petsistemi.domain.animation.PetAnimationState;
import com.petsistemi.domain.visual.PetSpriteAnimationDefinition;
import com.petsistemi.domain.visual.PetSpriteBillboard;
import com.petsistemi.domain.visual.PetSpriteDefinition;
import com.petsistemi.runtime.animation.PetAnimationTransition;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SpritePetRepresentationTest {

    @BeforeAll
    static void setUpServer() {
        MockBukkit.mock();
    }

    @AfterAll
    static void tearDownServer() {
        MockBukkit.unmock();
    }

    @Test
    void advancesFramesAndSwitchesAnimationWithoutRespawn() {
        ItemDisplayPetRepresentation delegate = mock(ItemDisplayPetRepresentation.class);
        ItemDisplay display = mock(ItemDisplay.class);
        UUID entityId = UUID.randomUUID();
        when(display.getUniqueId()).thenReturn(entityId);
        when(delegate.spawn(any(), any(), any())).thenReturn(display);
        when(delegate.isValid(display)).thenReturn(true);

        SpritePetRepresentation controller = new SpritePetRepresentation(delegate);
        PetDefinition definition = definition();
        PetInstance pet = pet();
        Player owner = mock(Player.class);

        assertSame(display, controller.spawn(pet, definition, owner));
        verify(display).setBillboard(Display.Billboard.VERTICAL);
        ArgumentCaptor<PetDefinition> delegatedDefinition = ArgumentCaptor.forClass(PetDefinition.class);
        verify(delegate).spawn(eq(pet), delegatedDefinition.capture(), eq(owner));
        assertEquals(14101, delegatedDefinition.getValue().representation().customModelData());

        controller.tickVisual(display, pet, definition, owner);
        controller.tickVisual(display, pet, definition, owner);
        ArgumentCaptor<ItemStack> stacks = ArgumentCaptor.forClass(ItemStack.class);
        verify(display).setItemStack(stacks.capture());
        assertEquals(14102, stacks.getValue().getItemMeta().getCustomModelData());

        controller.applyAnimation(display, pet, definition,
                new PetAnimationTransition(PetAnimationState.IDLE, null, PetAnimationState.MOVING, null));
        verify(display, times(2)).setItemStack(stacks.capture());
        assertEquals(14201, stacks.getAllValues().get(stacks.getAllValues().size() - 1)
                .getItemMeta().getCustomModelData());
        verify(delegate, never()).remove(display);
        assertTrue(controller.isValid(display));

        controller.remove(display);
        verify(delegate).remove(display);
        assertFalse(controller.isValid(display));
    }

    private static PetDefinition definition() {
        PetSpriteDefinition sprite = new PetSpriteDefinition("PAPER", PetSpriteBillboard.VERTICAL, Map.of(
                PetAnimationState.IDLE, new PetSpriteAnimationDefinition(2, true,
                        java.util.List.of(14101, 14102)),
                PetAnimationState.MOVING, new PetSpriteAnimationDefinition(1, false,
                        java.util.List.of(14201, 14202))));
        return PetDefinition.builder("pixel_slime", "Pixel Slime")
                .representation(PetRepresentationDefinition.sprite(sprite, PetVector3.ONE))
                .build();
    }

    private static PetInstance pet() {
        UUID owner = UUID.randomUUID();
        return new PetInstance(UUID.randomUUID(), owner, "pixel_slime", "Slime", 1, 0,
                PetAvailabilityState.AVAILABLE, 0, 0);
    }
}

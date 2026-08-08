package com.petsistemi.integration;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import com.petsistemi.api.PetService;
import com.petsistemi.api.result.PetGiveResult;
import com.petsistemi.definition.PetDefinitionRegistry;
import com.petsistemi.domain.PetDefinition;
import com.petsistemi.runtime.item.PetUnlockItemController;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PetUnlockItemTest {

    private ServerMock server;

    @BeforeEach void setUp() { server = MockBukkit.mock(); }
    @AfterEach void tearDown() { MockBukkit.unmock(); }

    @Test
    void markedItemRedeemsWithoutActivePet() {
        JavaPlugin plugin = MockBukkit.createMockPlugin("PetSistemi");
        PetDefinition wolf = PetDefinition.builder("wolf", "Kurt Dostu").build();
        PetDefinitionRegistry definitions = mock(PetDefinitionRegistry.class);
        when(definitions.find("wolf")).thenReturn(Optional.of(wolf));
        PetService service = mock(PetService.class);
        when(service.givePet(any(), eq("wolf"))).thenReturn(new PetGiveResult(true, "ok", null));
        PetUnlockItemController controller = new PetUnlockItemController(plugin, definitions, service);
        var player = server.addPlayer();

        ItemStack item = controller.create("WOLF", 2, Material.NAME_TAG);
        var result = controller.redeem(player, item).join();

        assertTrue(controller.matches(item));
        assertTrue(result.success(), result.message());
        verify(service).givePet(player.getUniqueId(), "wolf");
    }

    @Test
    void ordinaryItemIsRejected() {
        JavaPlugin plugin = MockBukkit.createMockPlugin("PetSistemi");
        PetDefinitionRegistry definitions = mock(PetDefinitionRegistry.class);
        PetUnlockItemController controller = new PetUnlockItemController(plugin, definitions, mock(PetService.class));
        assertFalse(controller.matches(new ItemStack(Material.NAME_TAG)));
    }
}

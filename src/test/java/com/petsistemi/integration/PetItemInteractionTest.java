package com.petsistemi.integration;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.WorldMock;
import com.petsistemi.api.item.PetItemActionResult;
import com.petsistemi.definition.PetDefinitionRegistry;
import com.petsistemi.domain.PetDefinition;
import com.petsistemi.domain.PetRuntimeState;
import com.petsistemi.domain.item.PetItemActionDefinition;
import com.petsistemi.listener.PetProtectionListener;
import com.petsistemi.runtime.ActivePet;
import com.petsistemi.runtime.ActivePetRegistry;
import com.petsistemi.runtime.item.PetItemActionEngine;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PetItemInteractionTest {

    private ServerMock server;
    private WorldMock world;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
    }

    @AfterEach
    void tearDown() { MockBukkit.unmock(); }

    @Test
    void ownerItemActionConsumesOnlyConfiguredAmount() {
        var player = server.addPlayer();
        Entity body = world.spawnEntity(new Location(world, 0, 64, 0), EntityType.WOLF);
        ActivePetRegistry activeRegistry = new ActivePetRegistry();
        ActivePet active = new ActivePet(UUID.randomUUID(), player.getUniqueId(), "wolf", 1,
                body.getUniqueId(), body, PetRuntimeState.ACTIVE);
        activeRegistry.register(active);

        NamespacedKey key = new NamespacedKey("test", "feed");
        PetDefinition definition = PetDefinition.builder("wolf", "Wolf").itemActions(List.of(
                new PetItemActionDefinition("feed", "BONE", null, 1, 0,
                        1, 0, null, key, Map.of()))).build();
        PetDefinitionRegistry definitions = mock(PetDefinitionRegistry.class);
        when(definitions.find("wolf")).thenReturn(Optional.of(definition));
        PetItemActionEngine engine = new PetItemActionEngine();
        engine.registerAction(key, (context, parameters) ->
                CompletableFuture.completedFuture(PetItemActionResult.success("fed")));
        JavaPlugin plugin = MockBukkit.createMockPlugin();
        PetProtectionListener listener = new PetProtectionListener(activeRegistry, null, plugin,
                definitions, null, null, null, engine);
        player.getInventory().setItemInMainHand(new ItemStack(Material.BONE, 2));
        PlayerInteractEntityEvent event = new PlayerInteractEntityEvent(player, body, EquipmentSlot.HAND);

        listener.onInteract(event);

        assertTrue(event.isCancelled());
        assertEquals(1, player.getInventory().getItemInMainHand().getAmount());
    }
}

package com.petsistemi.integration;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import com.petsistemi.gui.PetMenuHolder;
import com.petsistemi.gui.PetMenuListener;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The pet menu holds no real items — every stack in it is a button. If a click is not
 * cancelled the player can drag those buttons into their own inventory, which is an item
 * duplication exploit rather than a cosmetic bug.
 */
class PetMenuProtectionTest {

    private ServerMock server;
    private PetMenuListener listener;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        listener = new PetMenuListener(MockBukkit.createMockPlugin("PetSistemi"), null, null, null);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    /** A view onto an inventory owned by the pet menu holder. */
    private InventoryView petMenuView(PlayerMock player) {
        Inventory inventory = Bukkit.createInventory(new PetMenuHolder("PET_LIST_ALL", 0), 54);
        inventory.setItem(10, new ItemStack(Material.BONE));
        return player.openInventory(inventory);
    }

    /** A view onto an ordinary chest, which the listener must leave alone. */
    private InventoryView foreignView(PlayerMock player) {
        return player.openInventory(Bukkit.createInventory(null, InventoryType.CHEST));
    }

    private InventoryClickEvent click(InventoryView view, int slot, ClickType type) {
        return new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER, slot, type,
                InventoryAction.PICKUP_ALL);
    }

    @Test
    void plainClicksInThePetMenuAreCancelled() {
        InventoryClickEvent event = click(petMenuView(server.addPlayer("Eleven")), 10, ClickType.LEFT);

        listener.onInventoryClick(event);

        assertTrue(event.isCancelled(), "menü butonları alınabilir olmamalı");
    }

    /** Shift-click is the classic way to bulk-move items out of a GUI. */
    @Test
    void shiftClicksAreCancelled() {
        InventoryClickEvent event = click(petMenuView(server.addPlayer("Eleven")), 10, ClickType.SHIFT_LEFT);

        listener.onInventoryClick(event);

        assertTrue(event.isCancelled());
    }

    @Test
    void numberKeySwapsAreCancelled() {
        InventoryClickEvent event = click(petMenuView(server.addPlayer("Eleven")), 10, ClickType.NUMBER_KEY);

        listener.onInventoryClick(event);

        assertTrue(event.isCancelled());
    }

    @Test
    void doubleClickCollectIsCancelled() {
        InventoryClickEvent event = click(petMenuView(server.addPlayer("Eleven")), 10, ClickType.DOUBLE_CLICK);

        listener.onInventoryClick(event);

        assertTrue(event.isCancelled());
    }

    /** Clicking empty decoration slots must be cancelled too, not just item slots. */
    @Test
    void clicksOnEmptySlotsAreCancelled() {
        InventoryClickEvent event = click(petMenuView(server.addPlayer("Eleven")), 3, ClickType.LEFT);

        listener.onInventoryClick(event);

        assertTrue(event.isCancelled());
    }

    @Test
    void dragsAcrossThePetMenuAreCancelled() {
        PlayerMock player = server.addPlayer("Eleven");
        Map<Integer, ItemStack> dragged = new HashMap<>();
        dragged.put(10, new ItemStack(Material.BONE));
        InventoryDragEvent event = new InventoryDragEvent(petMenuView(player),
                null, new ItemStack(Material.BONE), false, dragged);

        listener.onInventoryDrag(event);

        assertTrue(event.isCancelled(), "menüye sürükleme engellenmeli");
    }

    /** The listener must not police inventories that are not ours. */
    @Test
    void clicksInAnUnrelatedInventoryAreUntouched() {
        InventoryClickEvent event = click(foreignView(server.addPlayer("Eleven")), 3, ClickType.LEFT);

        listener.onInventoryClick(event);

        assertFalse(event.isCancelled(), "yabancı envanterlere karışılmamalı");
    }
}

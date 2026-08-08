package com.petsistemi.runtime.item;

import com.petsistemi.api.item.PetItemActionResult;
import com.petsistemi.domain.PetDefinition;
import com.petsistemi.domain.PetRuntimeState;
import com.petsistemi.domain.item.PetItemActionDefinition;
import com.petsistemi.runtime.ActivePet;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PetItemActionEngineTest {

    private final NamespacedKey actionKey = new NamespacedKey("test", "feed");
    private PetItemActionEngine engine;
    private Player player;
    private ActivePet active;
    private PetDefinition definition;

    @BeforeEach
    void setUp() {
        engine = new PetItemActionEngine();
        player = mock(Player.class);
        UUID ownerId = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(ownerId);
        active = new ActivePet(UUID.randomUUID(), ownerId, "wolf", 10, UUID.randomUUID(), null, PetRuntimeState.ACTIVE);
        definition = PetDefinition.builder("wolf", "Wolf").itemActions(List.of(
                new PetItemActionDefinition("feed", "BONE", null, 1, 10,
                        1, 0, null, actionKey, Map.of("amount", 5)))).build();
    }

    @Test
    void successfulActionStartsCooldown() {
        engine.registerAction(actionKey, (context, parameters) ->
                CompletableFuture.completedFuture(PetItemActionResult.success("ok")));

        assertEquals(PetItemActionStatus.SUCCESS,
                engine.use(player, active, definition, new ItemStack(Material.BONE, 2)).join().status());
        PetItemActionOutcome blocked = engine.use(player, active, definition, new ItemStack(Material.BONE, 2)).join();
        assertEquals(PetItemActionStatus.COOLDOWN, blocked.status());
        assertEquals(10, blocked.remainingSeconds());
    }

    @Test
    void failedActionDoesNotStartCooldown() {
        AtomicInteger calls = new AtomicInteger();
        engine.registerAction(actionKey, (context, parameters) -> CompletableFuture.completedFuture(
                calls.incrementAndGet() == 1 ? PetItemActionResult.failure("no") : PetItemActionResult.success("ok")));

        assertEquals(PetItemActionStatus.FAILED,
                engine.use(player, active, definition, new ItemStack(Material.BONE)).join().status());
        assertEquals(PetItemActionStatus.SUCCESS,
                engine.use(player, active, definition, new ItemStack(Material.BONE)).join().status());
    }

    @Test
    void duplicateRequestIsBlockedWhileHandlerIsPending() {
        CompletableFuture<PetItemActionResult> pending = new CompletableFuture<>();
        engine.registerAction(actionKey, (context, parameters) -> pending);

        CompletableFuture<PetItemActionOutcome> first = engine.use(player, active, definition, new ItemStack(Material.BONE));
        assertEquals(PetItemActionStatus.PENDING,
                engine.use(player, active, definition, new ItemStack(Material.BONE)).join().status());
        pending.complete(PetItemActionResult.success("ok"));
        assertEquals(PetItemActionStatus.SUCCESS, first.join().status());
    }

    @Test
    void unmatchedItemPassesThroughAndShortStackIsRejected() {
        engine.registerAction(actionKey, (context, parameters) ->
                CompletableFuture.completedFuture(PetItemActionResult.success("ok")));
        assertEquals(PetItemActionStatus.NOT_MATCHED,
                engine.use(player, active, definition, new ItemStack(Material.STICK)).join().status());
        PetDefinition consumesTwo = definition.toBuilder().itemActions(List.of(
                new PetItemActionDefinition("feed", "BONE", null, 2, 0,
                        1, 0, null, actionKey, Map.of()))).build();
        assertEquals(PetItemActionStatus.INSUFFICIENT_ITEMS,
                engine.use(player, active, consumesTwo, new ItemStack(Material.BONE)).join().status());
    }
}

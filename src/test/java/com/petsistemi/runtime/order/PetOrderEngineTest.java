package com.petsistemi.runtime.order;

import com.petsistemi.api.order.PetOrderResult;
import com.petsistemi.definition.PetDefinitionRegistry;
import com.petsistemi.domain.PetDefinition;
import com.petsistemi.domain.PetFollowMode;
import com.petsistemi.domain.PetRuntimeState;
import com.petsistemi.runtime.ActivePet;
import com.petsistemi.runtime.ActivePetRegistry;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PetOrderEngineTest {

    private final NamespacedKey customKey = new NamespacedKey("test", "wave");
    private ActivePetRegistry activeRegistry;
    private PetDefinitionRegistry definitionRegistry;
    private PetOrderEngine engine;
    private Player player;
    private ActivePet active;
    private PetDefinition definition;

    @BeforeEach
    void setUp() {
        UUID ownerId = UUID.randomUUID();
        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(ownerId);
        Entity entity = mock(Entity.class);
        active = new ActivePet(UUID.randomUUID(), ownerId, "wolf", 4,
                UUID.randomUUID(), entity, PetRuntimeState.ACTIVE);
        definition = PetDefinition.builder("wolf", "Wolf").build();
        activeRegistry = new ActivePetRegistry();
        definitionRegistry = mock(PetDefinitionRegistry.class);
        when(definitionRegistry.find("wolf")).thenReturn(Optional.of(definition));
        engine = new PetOrderEngine(activeRegistry, definitionRegistry);
    }

    @Test
    void registeredOrderReceivesResolvedPetContext() {
        activeRegistry.register(active);
        AtomicReference<UUID> receivedPet = new AtomicReference<>();
        engine.registerOrder(customKey, context -> {
            receivedPet.set(context.petId());
            return CompletableFuture.completedFuture(PetOrderResult.success("ok"));
        });

        PetOrderResult result = engine.executeOrder(player, customKey).join();

        assertTrue(result.success());
        assertEquals(active.getPetId(), receivedPet.get());
    }

    @Test
    void orderRequiresAnActivePetAndRegisteredKey() {
        engine.registerOrder(customKey, context -> CompletableFuture.completedFuture(PetOrderResult.success("ok")));

        assertFalse(engine.executeOrder(player, customKey).join().success());
        assertFalse(engine.executeOrder(player, new NamespacedKey("test", "missing")).join().success());
    }

    @Test
    void duplicateOrderIsRejectedWhileFirstHandlerIsPending() {
        activeRegistry.register(active);
        CompletableFuture<PetOrderResult> pending = new CompletableFuture<>();
        engine.registerOrder(customKey, context -> pending);

        CompletableFuture<PetOrderResult> first = engine.executeOrder(player, customKey);
        PetOrderResult duplicate = engine.executeOrder(player, customKey).join();
        pending.complete(PetOrderResult.success("ok"));

        assertFalse(duplicate.success());
        assertTrue(first.join().success());
    }

    @Test
    void definitionAllowedModesGateBuiltInPersistentOrdersButNotCome() {
        definition = PetDefinition.builder("wolf", "Wolf")
                .allowedModes(List.of(PetFollowMode.FOLLOW)).build();
        when(definitionRegistry.find("wolf")).thenReturn(Optional.of(definition));
        activeRegistry.register(active);
        BuiltInPetOrders.register(engine, activeRegistry, null);

        assertFalse(engine.executeOrder(player, BuiltInPetOrders.STAY).join().success());
        assertFalse(engine.availableOrders(player).contains(BuiltInPetOrders.STAY));
        assertTrue(engine.availableOrders(player).contains(BuiltInPetOrders.COME));
    }

    @Test
    void comeTeleportsEveryTrackedEntityBehindOwner() {
        Entity primary = active.getSpawnedEntity();
        Entity child = mock(Entity.class);
        active.addChild(child);
        activeRegistry.register(active);
        when(player.getLocation()).thenReturn(new Location(null, 10, 64, 20));
        for (Entity entity : List.of(primary, child)) {
            when(entity.isValid()).thenReturn(true);
            when(entity.isDead()).thenReturn(false);
            when(entity.teleport(any(Location.class))).thenReturn(true);
        }
        BuiltInPetOrders.register(engine, activeRegistry, null);

        assertTrue(engine.executeOrder(player, BuiltInPetOrders.COME).join().success());
        verify(primary).teleport(any(Location.class));
        verify(child).teleport(any(Location.class));
    }
}

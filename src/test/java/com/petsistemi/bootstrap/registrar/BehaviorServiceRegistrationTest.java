package com.petsistemi.bootstrap.registrar;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import com.petsistemi.api.PetExperienceService;
import com.petsistemi.api.PetService;
import com.petsistemi.api.behavior.BehaviorService;
import com.petsistemi.api.model.ModelProviderService;
import com.petsistemi.api.item.PetItemActionService;
import com.petsistemi.api.item.PetUnlockItemService;
import com.petsistemi.api.order.PetOrderService;
import com.petsistemi.api.mount.PetMountService;
import com.petsistemi.api.network.PetNetworkSyncService;
import com.petsistemi.api.pack.PetPackService;
import com.petsistemi.api.marketplace.PetMarketplaceService;
import com.petsistemi.bootstrap.PetPluginContext;
import com.petsistemi.runtime.PetReactionEngine;
import com.petsistemi.runtime.behavior.PetBehaviorEngine;
import com.petsistemi.runtime.PetRepresentationRegistry;
import com.petsistemi.runtime.model.ModelProviderRegistry;
import com.petsistemi.runtime.item.PetItemActionEngine;
import com.petsistemi.runtime.item.PetUnlockItemController;
import com.petsistemi.runtime.order.PetOrderEngine;
import com.petsistemi.runtime.mount.PetMountController;
import com.petsistemi.network.DefaultPetNetworkSyncService;
import com.petsistemi.pack.DefaultPetPackService;
import com.petsistemi.marketplace.DefaultPetMarketplaceService;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BehaviorServiceRegistrationTest {
    @AfterEach void tearDown() { MockBukkit.unmock(); }

    @Test
    void thirdPartyPluginCanLoadBehaviorServiceFromBukkit() {
        ServerMock server = MockBukkit.mock();
        JavaPlugin plugin = MockBukkit.createMockPlugin();
        PetBehaviorEngine engine = new PetBehaviorEngine();
        PetReactionEngine reactions = mock(PetReactionEngine.class);
        when(reactions.behaviorEngine()).thenReturn(engine);
        PetPluginContext context = mock(PetPluginContext.class);
        when(context.plugin()).thenReturn(plugin);
        when(context.petService()).thenReturn(mock(PetService.class));
        when(context.experienceService()).thenReturn(mock(PetExperienceService.class));
        when(context.reactionEngine()).thenReturn(reactions);

        ServiceRegistrar.register(context);

        RegisteredServiceProvider<BehaviorService> registration =
                server.getServicesManager().getRegistration(BehaviorService.class);
        assertSame(engine, registration.getProvider());
    }

    @Test
    void thirdPartyPluginCanLoadModelProviderServiceFromBukkit() {
        ServerMock server = MockBukkit.mock();
        JavaPlugin plugin = MockBukkit.createMockPlugin();
        PetBehaviorEngine engine = new PetBehaviorEngine();
        PetReactionEngine reactions = mock(PetReactionEngine.class);
        when(reactions.behaviorEngine()).thenReturn(engine);
        ModelProviderRegistry models = new ModelProviderRegistry(plugin, new PetRepresentationRegistry(), null);
        PetPluginContext context = mock(PetPluginContext.class);
        when(context.plugin()).thenReturn(plugin);
        when(context.petService()).thenReturn(mock(PetService.class));
        when(context.experienceService()).thenReturn(mock(PetExperienceService.class));
        when(context.reactionEngine()).thenReturn(reactions);
        when(context.modelProviderRegistry()).thenReturn(models);

        ServiceRegistrar.register(context);

        RegisteredServiceProvider<ModelProviderService> registration =
                server.getServicesManager().getRegistration(ModelProviderService.class);
        assertSame(models, registration.getProvider());
    }

    @Test
    void thirdPartyPluginCanLoadItemActionServiceFromBukkit() {
        ServerMock server = MockBukkit.mock();
        JavaPlugin plugin = MockBukkit.createMockPlugin();
        PetBehaviorEngine behaviors = new PetBehaviorEngine();
        PetReactionEngine reactions = mock(PetReactionEngine.class);
        when(reactions.behaviorEngine()).thenReturn(behaviors);
        PetItemActionEngine itemActions = new PetItemActionEngine();
        PetPluginContext context = mock(PetPluginContext.class);
        when(context.plugin()).thenReturn(plugin);
        when(context.petService()).thenReturn(mock(PetService.class));
        when(context.experienceService()).thenReturn(mock(PetExperienceService.class));
        when(context.reactionEngine()).thenReturn(reactions);
        when(context.itemActionEngine()).thenReturn(itemActions);

        ServiceRegistrar.register(context);

        RegisteredServiceProvider<PetItemActionService> registration =
                server.getServicesManager().getRegistration(PetItemActionService.class);
        assertSame(itemActions, registration.getProvider());
    }

    @Test
    void thirdPartyPluginCanLoadUnlockItemServiceFromBukkit() {
        ServerMock server = MockBukkit.mock();
        JavaPlugin plugin = MockBukkit.createMockPlugin();
        PetReactionEngine reactions = mock(PetReactionEngine.class);
        when(reactions.behaviorEngine()).thenReturn(new PetBehaviorEngine());
        PetUnlockItemController unlockItems = mock(PetUnlockItemController.class);
        PetPluginContext context = mock(PetPluginContext.class);
        when(context.plugin()).thenReturn(plugin);
        when(context.petService()).thenReturn(mock(PetService.class));
        when(context.experienceService()).thenReturn(mock(PetExperienceService.class));
        when(context.reactionEngine()).thenReturn(reactions);
        when(context.unlockItemController()).thenReturn(unlockItems);

        ServiceRegistrar.register(context);

        RegisteredServiceProvider<PetUnlockItemService> registration =
                server.getServicesManager().getRegistration(PetUnlockItemService.class);
        assertSame(unlockItems, registration.getProvider());
    }

    @Test
    void thirdPartyPluginCanLoadOrderServiceFromBukkit() {
        ServerMock server = MockBukkit.mock();
        JavaPlugin plugin = MockBukkit.createMockPlugin();
        PetReactionEngine reactions = mock(PetReactionEngine.class);
        when(reactions.behaviorEngine()).thenReturn(new PetBehaviorEngine());
        PetOrderEngine orders = mock(PetOrderEngine.class);
        PetPluginContext context = mock(PetPluginContext.class);
        when(context.plugin()).thenReturn(plugin);
        when(context.petService()).thenReturn(mock(PetService.class));
        when(context.experienceService()).thenReturn(mock(PetExperienceService.class));
        when(context.reactionEngine()).thenReturn(reactions);
        when(context.orderEngine()).thenReturn(orders);

        ServiceRegistrar.register(context);

        RegisteredServiceProvider<PetOrderService> registration =
                server.getServicesManager().getRegistration(PetOrderService.class);
        assertSame(orders, registration.getProvider());
    }

    @Test
    void thirdPartyPluginCanLoadMountServiceFromBukkit() {
        ServerMock server = MockBukkit.mock();
        JavaPlugin plugin = MockBukkit.createMockPlugin();
        PetReactionEngine reactions = mock(PetReactionEngine.class);
        when(reactions.behaviorEngine()).thenReturn(new PetBehaviorEngine());
        PetMountController mounts = mock(PetMountController.class);
        PetPluginContext context = mock(PetPluginContext.class);
        when(context.plugin()).thenReturn(plugin);
        when(context.petService()).thenReturn(mock(PetService.class));
        when(context.experienceService()).thenReturn(mock(PetExperienceService.class));
        when(context.reactionEngine()).thenReturn(reactions);
        when(context.mountController()).thenReturn(mounts);

        ServiceRegistrar.register(context);

        RegisteredServiceProvider<PetMountService> registration =
                server.getServicesManager().getRegistration(PetMountService.class);
        assertSame(mounts, registration.getProvider());
    }

    @Test
    void thirdPartyPluginCanLoadEcosystemServicesFromBukkit() {
        ServerMock server = MockBukkit.mock();
        JavaPlugin plugin = MockBukkit.createMockPlugin();
        PetReactionEngine reactions = mock(PetReactionEngine.class);
        when(reactions.behaviorEngine()).thenReturn(new PetBehaviorEngine());
        DefaultPetNetworkSyncService network = mock(DefaultPetNetworkSyncService.class);
        DefaultPetPackService packs = mock(DefaultPetPackService.class);
        DefaultPetMarketplaceService marketplace = mock(DefaultPetMarketplaceService.class);
        PetPluginContext context = mock(PetPluginContext.class);
        when(context.plugin()).thenReturn(plugin);
        when(context.petService()).thenReturn(mock(PetService.class));
        when(context.experienceService()).thenReturn(mock(PetExperienceService.class));
        when(context.reactionEngine()).thenReturn(reactions);
        when(context.networkSyncService()).thenReturn(network);
        when(context.petPackService()).thenReturn(packs);
        when(context.marketplaceService()).thenReturn(marketplace);

        ServiceRegistrar.register(context);

        assertSame(network, server.getServicesManager().getRegistration(PetNetworkSyncService.class).getProvider());
        assertSame(packs, server.getServicesManager().getRegistration(PetPackService.class).getProvider());
        assertSame(marketplace, server.getServicesManager().getRegistration(PetMarketplaceService.class).getProvider());
    }
}

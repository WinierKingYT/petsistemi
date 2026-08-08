package com.petsistemi.bootstrap.registrar;

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
import org.bukkit.Bukkit;
import org.bukkit.plugin.ServicePriority;

public final class ServiceRegistrar {

    public static void register(PetPluginContext context) {
        Bukkit.getServicesManager().register(PetService.class, context.petService(), context.plugin(), ServicePriority.Normal);
        Bukkit.getServicesManager().register(PetExperienceService.class, context.experienceService(), context.plugin(), ServicePriority.Normal);
        Bukkit.getServicesManager().register(BehaviorService.class, context.reactionEngine().behaviorEngine(), context.plugin(), ServicePriority.Normal);
        if (context.itemActionEngine() != null) {
            Bukkit.getServicesManager().register(PetItemActionService.class, context.itemActionEngine(), context.plugin(), ServicePriority.Normal);
        }
        if (context.unlockItemController() != null) {
            Bukkit.getServicesManager().register(PetUnlockItemService.class, context.unlockItemController(), context.plugin(), ServicePriority.Normal);
        }
        if (context.orderEngine() != null) {
            Bukkit.getServicesManager().register(PetOrderService.class, context.orderEngine(), context.plugin(), ServicePriority.Normal);
        }
        if (context.mountController() != null) {
            Bukkit.getServicesManager().register(PetMountService.class, context.mountController(), context.plugin(), ServicePriority.Normal);
        }
        if (context.networkSyncService() != null) {
            Bukkit.getServicesManager().register(PetNetworkSyncService.class, context.networkSyncService(),
                    context.plugin(), ServicePriority.Normal);
        }
        if (context.petPackService() != null) {
            Bukkit.getServicesManager().register(PetPackService.class, context.petPackService(),
                    context.plugin(), ServicePriority.Normal);
        }
        if (context.marketplaceService() != null) {
            Bukkit.getServicesManager().register(PetMarketplaceService.class, context.marketplaceService(),
                    context.plugin(), ServicePriority.Normal);
        }
        if (context.modelProviderRegistry() != null) {
            Bukkit.getServicesManager().register(ModelProviderService.class, context.modelProviderRegistry(),
                    context.plugin(), ServicePriority.Normal);
        }
    }

    public static void unregister(PetPluginContext context) {
        if (context != null && context.plugin() != null) {
            Bukkit.getServicesManager().unregisterAll(context.plugin());
        }
    }
}

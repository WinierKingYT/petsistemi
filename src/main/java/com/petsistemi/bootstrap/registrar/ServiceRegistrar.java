package com.petsistemi.bootstrap.registrar;

import com.petsistemi.api.PetExperienceService;
import com.petsistemi.api.PetService;
import com.petsistemi.bootstrap.PetPluginContext;
import org.bukkit.Bukkit;
import org.bukkit.plugin.ServicePriority;

public final class ServiceRegistrar {

    public static void register(PetPluginContext context) {
        Bukkit.getServicesManager().register(PetService.class, context.petService(), context.plugin(), ServicePriority.Normal);
        Bukkit.getServicesManager().register(PetExperienceService.class, context.experienceService(), context.plugin(), ServicePriority.Normal);
    }

    public static void unregister(PetPluginContext context) {
        if (context != null && context.plugin() != null) {
            Bukkit.getServicesManager().unregisterAll(context.plugin());
        }
    }
}

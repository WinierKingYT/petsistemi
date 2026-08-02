package com.petsistemi.bootstrap.registrar;

import com.petsistemi.bootstrap.PetPluginContext;
import com.petsistemi.gui.PetMenuListener;
import com.petsistemi.listener.PetEntityListener;
import com.petsistemi.listener.PetProtectionListener;
import com.petsistemi.listener.PlayerConnectionListener;
import com.petsistemi.listener.WorldChangeListener;
import org.bukkit.Bukkit;

public final class ListenerRegistrar {

    public static void register(PetPluginContext context) {
        Bukkit.getPluginManager().registerEvents(new PetMenuListener(context.petService()), context.plugin());
        Bukkit.getPluginManager().registerEvents(new PlayerConnectionListener(context.plugin(), context.petService(), context.coordinator()), context.plugin());
        Bukkit.getPluginManager().registerEvents(new PetEntityListener(context.activePetRegistry(), context.coordinator()), context.plugin());
        Bukkit.getPluginManager().registerEvents(new PetProtectionListener(context.activePetRegistry()), context.plugin());
        Bukkit.getPluginManager().registerEvents(new WorldChangeListener(context.plugin(), context.activePetRegistry()), context.plugin());
    }
}

package com.petsistemi.bootstrap.registrar;

import com.petsistemi.bootstrap.PetPluginContext;
import com.petsistemi.gui.PetMenuListener;
import com.petsistemi.gui.RenameInputSessionListener;
import com.petsistemi.listener.PetEntityListener;
import com.petsistemi.listener.PetProtectionListener;
import com.petsistemi.listener.PlayerConnectionListener;
import com.petsistemi.listener.WorldChangeListener;
import org.bukkit.Bukkit;

public final class ListenerRegistrar {

    public static void register(PetPluginContext context) {
        Bukkit.getPluginManager().registerEvents(new PetMenuListener(context.plugin(), context.petService(), context.sessionManager(), context.definitionRegistry()), context.plugin());
        Bukkit.getPluginManager().registerEvents(new RenameInputSessionListener(context.plugin(), context.petService(), context.sessionManager(), context.definitionRegistry()), context.plugin());
        Bukkit.getPluginManager().registerEvents(new PlayerConnectionListener(context.plugin(), context.coordinator(), context.profileCache(), context.dbExecutor()), context.plugin());
        Bukkit.getPluginManager().registerEvents(new PetEntityListener(context.activePetRegistry(), context.coordinator()), context.plugin());
        Bukkit.getPluginManager().registerEvents(new PetProtectionListener(context.activePetRegistry()), context.plugin());
        Bukkit.getPluginManager().registerEvents(new WorldChangeListener(context.plugin(), context.activePetRegistry(), context.coordinator()), context.plugin());
        Bukkit.getPluginManager().registerEvents(new com.petsistemi.listener.PetLevelUpListener(), context.plugin());
        Bukkit.getPluginManager().registerEvents(new com.petsistemi.listener.PetProgressionListener(context.activePetRegistry(), context.experienceService(), context.configSnapshot()), context.plugin());
    }
}

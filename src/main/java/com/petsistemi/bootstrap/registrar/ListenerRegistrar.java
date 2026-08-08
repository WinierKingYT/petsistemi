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
        Bukkit.getPluginManager().registerEvents(new PetMenuListener(context.plugin(), context.operationService(), context.petService(), context.sessionManager(), context.definitionRegistry(), context.mainThreadDispatcher(), context.messageService(), context.configSnapshot(), context.editorSessionManager()), context.plugin());
        Bukkit.getPluginManager().registerEvents(new RenameInputSessionListener(context.plugin(), context.petService(), context.sessionManager(), context.definitionRegistry(), context.messageService(), context.configSnapshot()), context.plugin());
        Bukkit.getPluginManager().registerEvents(new com.petsistemi.gui.PetEditorInputListener(context.plugin(), context.editorSessionManager()), context.plugin());
        Bukkit.getPluginManager().registerEvents(new PlayerConnectionListener(context.plugin(), context.coordinator(), context.operationService(), context.profileCache(), context.dbExecutor(), context.mainThreadDispatcher()), context.plugin());
        Bukkit.getPluginManager().registerEvents(new PetEntityListener(context.activePetRegistry(), context.coordinator()), context.plugin());
        // Sole handler for right-clicks on pets: bodies, tracked children and interaction hitboxes.
        Bukkit.getPluginManager().registerEvents(new PetProtectionListener(context.activePetRegistry(), context.petService(), context.plugin(), context.definitionRegistry(), context.configSnapshot(), context.messageService(), context.hitboxController(), context.itemActionEngine(), context.mountController()), context.plugin());
        Bukkit.getPluginManager().registerEvents(new WorldChangeListener(context.plugin(), context.activePetRegistry(), context.coordinator(), context.operationService()), context.plugin());
        Bukkit.getPluginManager().registerEvents(new com.petsistemi.listener.PetLevelUpListener(context.messageService()), context.plugin());
        Bukkit.getPluginManager().registerEvents(new com.petsistemi.listener.PetProgressionListener(context.activePetRegistry(), context.experienceService(), context.configSnapshot()), context.plugin());
        Bukkit.getPluginManager().registerEvents(new com.petsistemi.listener.PetRuntimeSyncListener(context.activePetRegistry(), context.coordinator()), context.plugin());
        Bukkit.getPluginManager().registerEvents(new com.petsistemi.listener.PetReactionListener(
                context.activePetRegistry(), context.reactionEngine(), context.definitionRegistry()), context.plugin());
        Bukkit.getPluginManager().registerEvents(new com.petsistemi.listener.PlayerProfilePrewarmListener(context.petService()), context.plugin());
        Bukkit.getPluginManager().registerEvents(
                new com.petsistemi.listener.PetAbilityBindingListener(context.abilityBindingController()), context.plugin());
        if (context.unlockItemController() != null) {
            Bukkit.getPluginManager().registerEvents(
                    new com.petsistemi.listener.PetUnlockItemListener(context.plugin(), context.unlockItemController()), context.plugin());
        }
    }
}

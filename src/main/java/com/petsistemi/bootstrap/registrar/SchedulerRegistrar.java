package com.petsistemi.bootstrap.registrar;

import com.petsistemi.bootstrap.PetPluginContext;
import com.petsistemi.runtime.ActivePet;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

public final class SchedulerRegistrar {

    public static void register(PetPluginContext context) {
        long tickInterval = context.plugin().getConfig().getLong("runtime.tick-interval-ticks", 5L);

        // Behavior Ticking Task
        BukkitTask behaviorTask = Bukkit.getScheduler().runTaskTimer(context.plugin(), () -> {
            for (ActivePet activePet : context.activePetRegistry().getAllActive()) {
                Player owner = Bukkit.getPlayer(activePet.getOwnerId());
                if (owner != null && owner.isOnline()) {
                    Entity entity = activePet.getSpawnedEntity();
                    if (entity instanceof LivingEntity living) {
                        context.behaviorController().tick(activePet, living, owner);
                    }
                }
            }
        }, 20L, tickInterval);

        context.taskRegistry().register(behaviorTask);

        // Watchdog Task
        BukkitTask watchdogTask = Bukkit.getScheduler().runTaskTimer(context.plugin(), () -> {
            if (context.coordinator() != null) {
                context.coordinator().runWatchdogCheck();
            }
        }, 100L, 100L);

        context.taskRegistry().register(watchdogTask);

        // Online Players Reload Restore Task
        Bukkit.getScheduler().runTaskLater(context.plugin(), () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                context.petService().getSelectedPet(player.getUniqueId()).ifPresent(snapshot -> {
                    if (context.activePetRegistry().getByOwner(player.getUniqueId()).isEmpty()) {
                        context.coordinator().restoreOnJoin(player);
                    }
                });
            }
        }, 20L);
    }
}

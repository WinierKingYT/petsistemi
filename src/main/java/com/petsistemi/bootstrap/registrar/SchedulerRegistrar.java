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

        // Milestone 2 Tasks: Passive XP, Ability Buffs, Magnet, Particles
        BukkitTask passiveXpTask = Bukkit.getScheduler().runTaskTimer(
                context.plugin(),
                new com.petsistemi.progression.PetPassiveXpTask(context.activePetRegistry(), context.experienceService(), context.plugin().getConfig()),
                1200L, 1200L
        );
        context.taskRegistry().register(passiveXpTask);

        var features = context.config() != null ? context.config().features() : null;

        if (features != null && features.abilitiesEnabled()) {
            BukkitTask abilityTask = Bukkit.getScheduler().runTaskTimer(
                    context.plugin(),
                    new com.petsistemi.runtime.task.PetAbilityTask(context.activePetRegistry(), context.petRepository(), context.definitionRegistry()),
                    40L, 40L
            );
            context.taskRegistry().register(abilityTask);
        }

        if (features != null && features.magnetEnabled()) {
            BukkitTask magnetTask = Bukkit.getScheduler().runTaskTimer(
                    context.plugin(),
                    new com.petsistemi.runtime.task.PetMagnetTask(context.activePetRegistry()),
                    20L, 20L
            );
            context.taskRegistry().register(magnetTask);
        }

        if (features != null && features.particlesEnabled()) {
            BukkitTask particleTask = Bukkit.getScheduler().runTaskTimer(
                    context.plugin(),
                    new com.petsistemi.runtime.task.PetParticleTask(context.activePetRegistry(), context.petRepository()),
                    10L, 10L
            );
            context.taskRegistry().register(particleTask);
        }
    }
}

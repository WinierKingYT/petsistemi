package com.petsistemi.bootstrap.registrar;

import com.petsistemi.bootstrap.PetPluginContext;
import com.petsistemi.config.RuntimeConfigurationSnapshot;
import com.petsistemi.runtime.ActivePet;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

public final class SchedulerRegistrar {

    public static void register(PetPluginContext context) {
        // Watchdog Task (Fixed lifecycle)
        BukkitTask watchdogTask = Bukkit.getScheduler().runTaskTimer(context.plugin(), () -> {
            if (context.coordinator() != null) {
                context.coordinator().runWatchdogCheck();
            }
        }, 100L, 100L);
        context.taskRegistry().registerNamed("watchdogTask", watchdogTask);

        Bukkit.getScheduler().runTaskLater(context.plugin(), () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (context.activePetRegistry().getByOwner(player.getUniqueId()).isEmpty()) {
                    if (context.operationService() != null) {
                        context.operationService().restoreSelectedPetAsync(player);
                    }
                }
            }
        }, 20L);

        // Single entrypoint for reloadable tasks
        reevaluateReloadableTasks(context);
    }

    public static void reevaluateReloadableTasks(PetPluginContext context) {
        RuntimeConfigurationSnapshot snapshot = (context.configSnapshot() != null) ? context.configSnapshot().get() : null;
        var config = (snapshot != null) ? snapshot.configuration() : null;
        var runtime = (config != null) ? config.runtime() : null;
        var features = (config != null) ? config.features() : null;

        long tickInterval = (runtime != null) ? runtime.tickIntervalTicks() : 5L;
        if (tickInterval < 1L) tickInterval = 5L;

        // 1. Behavior Ticking Task
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
        context.taskRegistry().registerNamed("behaviorTask", behaviorTask);

        // 2. Passive XP Task
        BukkitTask passiveXpTask = Bukkit.getScheduler().runTaskTimer(
                context.plugin(),
                new com.petsistemi.progression.PetPassiveXpTask(context.activePetRegistry(), context.experienceService(), context.configSnapshot()),
                1200L, 1200L
        );
        context.taskRegistry().registerNamed("passiveXpTask", passiveXpTask);

        // 3. Ability Task
        if (features != null && features.abilitiesEnabled()) {
            BukkitTask abilityTask = Bukkit.getScheduler().runTaskTimer(
                    context.plugin(),
                    new com.petsistemi.runtime.task.PetAbilityTask(context.activePetRegistry(), context.petRepository(), context.definitionRegistry()),
                    40L, 40L
            );
            context.taskRegistry().registerNamed("abilityTask", abilityTask);
        } else {
            context.taskRegistry().cancelNamed("abilityTask");
        }

        // 4. Magnet Task
        if (features != null && features.magnetEnabled()) {
            BukkitTask magnetTask = Bukkit.getScheduler().runTaskTimer(
                    context.plugin(),
                    new com.petsistemi.runtime.task.PetMagnetTask(context.activePetRegistry()),
                    20L, 20L
            );
            context.taskRegistry().registerNamed("magnetTask", magnetTask);
        } else {
            context.taskRegistry().cancelNamed("magnetTask");
        }

        // 5. Particle Task
        if (features != null && features.particlesEnabled()) {
            BukkitTask particleTask = Bukkit.getScheduler().runTaskTimer(
                    context.plugin(),
                    new com.petsistemi.runtime.task.PetParticleTask(context.activePetRegistry(), context.petRepository()),
                    10L, 10L
            );
            context.taskRegistry().registerNamed("particleTask", particleTask);
        } else {
            context.taskRegistry().cancelNamed("particleTask");
        }
    }

    public static void reevaluateFeatureTasks(PetPluginContext context) {
        reevaluateReloadableTasks(context);
    }
}

package com.petsistemi.bootstrap.registrar;

import com.petsistemi.bootstrap.PetPluginContext;
import com.petsistemi.config.RuntimeConfigurationSnapshot;
import com.petsistemi.runtime.task.PetRuntimeTickTask;
import org.bukkit.Bukkit;
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

        if (context.networkSyncService() != null) {
            long interval = Math.max(1L, context.config().ecosystem().network().pollIntervalTicks());
            BukkitTask networkSyncTask = Bukkit.getScheduler().runTaskTimer(
                    context.plugin(), () -> context.networkSyncService().pollOnceAsync(), interval, interval);
            context.taskRegistry().registerNamed("networkSyncTask", networkSyncTask);
        }

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
        var features = (config != null) ? config.features() : null;

        // 1. Runtime Tick Task (single entrypoint: movement/behavior for all pets).
        //    Runs every game tick; per-pet cadence via update-interval-ticks.
        BukkitTask runtimeTickTask = Bukkit.getScheduler().runTaskTimer(
                context.plugin(),
                new PetRuntimeTickTask(context.coordinator()),
                20L, 1L
        );
        context.taskRegistry().registerNamed("runtimeTickTask", runtimeTickTask);

        // 2. Passive XP Task
        BukkitTask passiveXpTask = Bukkit.getScheduler().runTaskTimer(
                context.plugin(),
                new com.petsistemi.progression.PetPassiveXpTask(context.activePetRegistry(), context.experienceService(), context.configSnapshot()),
                1200L, 1200L
        );
        context.taskRegistry().registerNamed("passiveXpTask", passiveXpTask);

        // Buffs used to live in a separate task that switched on the pet's id in Java, so
        // wolf/cat/allay granted effects no YAML could describe or turn off. They are now
        // declared in each pet's `buffs:` block and applied by PetBuffController inside the
        // runtime tick; `features.buffs.enabled` is the server-wide kill switch.
        context.taskRegistry().cancelNamed("abilityTask");

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
                    new com.petsistemi.runtime.task.PetParticleTask(context.activePetRegistry()),
                    10L, 10L
            );
            context.taskRegistry().registerNamed("particleTask", particleTask);
        } else {
            context.taskRegistry().cancelNamed("particleTask");
        }

        // 6. Auto Backup Task (every 6 hours)
        boolean sqliteBackend = config == null || com.petsistemi.persistence.DatabaseBackend.from(
                config.database().backend()) == com.petsistemi.persistence.DatabaseBackend.SQLITE;
        if (context.adminPersistenceService() != null && sqliteBackend) {
            BukkitTask autoBackupTask = Bukkit.getScheduler().runTaskTimerAsynchronously(
                    context.plugin(),
                    new com.petsistemi.task.AutoBackupTask(context.plugin(), context.adminPersistenceService(),
                            context.configSnapshot() != null && context.configSnapshot().get() != null
                                    && context.configSnapshot().get().configuration() != null
                                    ? context.configSnapshot().get().configuration().database().maxBackups()
                                    : 5),
                    43200L, 432000L
            );
            context.taskRegistry().registerNamed("autoBackupTask", autoBackupTask);
        } else {
            context.taskRegistry().cancelNamed("autoBackupTask");
        }

        // 7. Orphan Cleaner Task (every 10 minutes)
        BukkitTask orphanCleanerTask = Bukkit.getScheduler().runTaskTimer(
                context.plugin(),
                // Must ask the coordinator, not the registry: a pet mid-summon is spawned
                // before it is registered, and sweeping on the registry alone deletes it.
                new com.petsistemi.task.OrphanCleanerTask(context.plugin(), context.coordinator()::isKnownPet),
                1200L, 12000L
        );
        context.taskRegistry().registerNamed("orphanCleanerTask", orphanCleanerTask);
    }

    public static void reevaluateFeatureTasks(PetPluginContext context) {
        reevaluateReloadableTasks(context);
    }
}

package com.petsistemi.bootstrap;

import org.bukkit.scheduler.BukkitTask;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class TaskRegistry {

    private final Set<BukkitTask> tasks = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public void register(BukkitTask task) {
        if (task != null) {
            tasks.add(task);
        }
    }

    public void cancelAll() {
        for (BukkitTask task : tasks) {
            try {
                if (!task.isCancelled()) {
                    task.cancel();
                }
            } catch (Exception ignored) {}
        }
        tasks.clear();
    }
}

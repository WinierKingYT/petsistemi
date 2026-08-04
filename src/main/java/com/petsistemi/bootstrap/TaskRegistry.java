package com.petsistemi.bootstrap;

import org.bukkit.scheduler.BukkitTask;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class TaskRegistry {

    private final Set<BukkitTask> tasks = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Map<String, BukkitTask> namedTasks = new ConcurrentHashMap<>();

    public void register(BukkitTask task) {
        if (task != null) {
            tasks.add(task);
        }
    }

    public void registerNamed(String key, BukkitTask task) {
        cancelNamed(key);
        if (task != null) {
            namedTasks.put(key, task);
        }
    }

    public void cancelNamed(String key) {
        BukkitTask task = namedTasks.remove(key);
        if (task != null && !task.isCancelled()) {
            try {
                task.cancel();
            } catch (Exception ignored) {}
        }
    }

    public int size() {
        return tasks.size() + namedTasks.size();
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

        for (BukkitTask task : namedTasks.values()) {
            try {
                if (!task.isCancelled()) {
                    task.cancel();
                }
            } catch (Exception ignored) {}
        }
        namedTasks.clear();
    }
}

package com.petsistemi.bootstrap;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public class BukkitMainThreadDispatcher implements MainThreadDispatcher {

    private final JavaPlugin plugin;

    public BukkitMainThreadDispatcher(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin null olamaz.");
    }

    @Override
    public boolean isMainThread() {
        return Bukkit.isPrimaryThread();
    }

    @Override
    public CompletableFuture<Void> run(Runnable action) {
        Objects.requireNonNull(action, "action null olamaz.");
        CompletableFuture<Void> future = new CompletableFuture<>();

        if (!plugin.isEnabled()) {
            future.completeExceptionally(new IllegalStateException("Plugin devre dışı bırakıldığı için main-thread işlemi iptal edildi."));
            return future;
        }

        if (isMainThread()) {
            try {
                action.run();
                future.complete(null);
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        } else {
            try {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    try {
                        action.run();
                        future.complete(null);
                    } catch (Throwable t) {
                        future.completeExceptionally(t);
                    }
                });
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        }
        return future;
    }

    @Override
    public <T> CompletableFuture<T> supply(Supplier<T> supplier) {
        Objects.requireNonNull(supplier, "supplier null olamaz.");
        CompletableFuture<T> future = new CompletableFuture<>();

        if (!plugin.isEnabled()) {
            future.completeExceptionally(new IllegalStateException("Plugin devre dışı bırakıldığı için main-thread işlemi iptal edildi."));
            return future;
        }

        if (isMainThread()) {
            try {
                future.complete(supplier.get());
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        } else {
            try {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    try {
                        future.complete(supplier.get());
                    } catch (Throwable t) {
                        future.completeExceptionally(t);
                    }
                });
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        }
        return future;
    }
}

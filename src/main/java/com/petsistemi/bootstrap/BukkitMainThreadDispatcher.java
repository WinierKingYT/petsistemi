package com.petsistemi.bootstrap;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public class BukkitMainThreadDispatcher implements MainThreadDispatcher {

    private final JavaPlugin plugin;

    public BukkitMainThreadDispatcher(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean isMainThread() {
        try {
            return Bukkit.isPrimaryThread();
        } catch (Exception e) {
            return true; // Fallback for testing environments
        }
    }

    @Override
    public CompletableFuture<Void> run(Runnable action) {
        Objects.requireNonNull(action, "action null olamaz.");
        CompletableFuture<Void> future = new CompletableFuture<>();

        if (plugin != null && !plugin.isEnabled()) {
            future.completeExceptionally(new IllegalStateException("Plugin devre dışı bırakıldığı için işlem iptal edildi."));
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
            if (plugin == null) {
                try {
                    action.run();
                    future.complete(null);
                } catch (Throwable t) {
                    future.completeExceptionally(t);
                }
            } else {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    try {
                        action.run();
                        future.complete(null);
                    } catch (Throwable t) {
                        future.completeExceptionally(t);
                    }
                });
            }
        }
        return future;
    }

    @Override
    public <T> CompletableFuture<T> supply(Supplier<T> supplier) {
        Objects.requireNonNull(supplier, "supplier null olamaz.");
        CompletableFuture<T> future = new CompletableFuture<>();

        if (plugin != null && !plugin.isEnabled()) {
            future.completeExceptionally(new IllegalStateException("Plugin devre dışı bırakıldığı için işlem iptal edildi."));
            return future;
        }

        if (isMainThread()) {
            try {
                future.complete(supplier.get());
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        } else {
            if (plugin == null) {
                try {
                    future.complete(supplier.get());
                } catch (Throwable t) {
                    future.completeExceptionally(t);
                }
            } else {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    try {
                        future.complete(supplier.get());
                    } catch (Throwable t) {
                        future.completeExceptionally(t);
                    }
                });
            }
        }
        return future;
    }
}

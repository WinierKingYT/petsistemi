package com.petsistemi.persistence;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public final class DatabaseExecutor implements AutoCloseable {

    private final ExecutorService executor;
    private final Logger logger;

    public DatabaseExecutor(Logger logger) {
        this.logger = logger;
        this.executor = Executors.newSingleThreadExecutor(r -> new Thread(r, "PetSistemi-Database-1"));
    }

    public <T> CompletableFuture<T> submit(Callable<T> task) {
        CompletableFuture<T> future = new CompletableFuture<>();
        executor.submit(() -> {
            try {
                future.complete(task.call());
            } catch (Throwable t) {
                logger.severe("Database task hatası: " + t.getMessage());
                future.completeExceptionally(t);
            }
        });
        return future;
    }

    public CompletableFuture<Void> runAsync(Runnable runnable) {
        return submit(() -> {
            runnable.run();
            return null;
        });
    }

    public CompletableFuture<Void> executeAsync(Runnable runnable) {
        return runAsync(runnable);
    }

    @Override
    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}

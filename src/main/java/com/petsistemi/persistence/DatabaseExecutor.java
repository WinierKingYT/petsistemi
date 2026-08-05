package com.petsistemi.persistence;

import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class DatabaseExecutor implements AutoCloseable {

    private final ThreadPoolExecutor poolExecutor;
    private final ExecutorService executor;
    private final Logger logger;
    private final Thread dbThread;
    private volatile boolean closed = false;

    public DatabaseExecutor(Logger logger) {
        this.logger = logger;
        Thread[] threadHolder = new Thread[1];
        this.poolExecutor = new ThreadPoolExecutor(
                1, 1,
                0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(),
                r -> {
                    Thread t = new Thread(r, "PetSistemi-Database-1");
                    t.setUncaughtExceptionHandler((th, ex) -> {
                        if (logger != null) {
                            logger.log(Level.SEVERE, "Database Thread Uncaught Exception [" + th.getName() + "]: " + ex.getMessage(), ex);
                        }
                    });
                    threadHolder[0] = t;
                    return t;
                }
        );
        this.executor = poolExecutor;
        try {
            this.executor.submit(() -> {}).get();
        } catch (Exception ignored) {}
        this.dbThread = threadHolder[0];
    }

    public boolean isDatabaseThread() {
        return Thread.currentThread() == dbThread;
    }

    public boolean isClosed() {
        return closed || poolExecutor.isShutdown();
    }

    public int pendingTaskCount() {
        return poolExecutor.getQueue().size();
    }

    public <T> CompletableFuture<T> submit(Callable<T> task) {
        Objects.requireNonNull(task, "task null olamaz.");
        CompletableFuture<T> future = new CompletableFuture<>();

        if (isClosed()) {
            future.completeExceptionally(new RejectedExecutionException("DatabaseExecutor kapatıldı, yeni görev kabul edilmiyor."));
            return future;
        }

        try {
            poolExecutor.submit(() -> {
                try {
                    future.complete(task.call());
                } catch (Throwable t) {
                    if (logger != null) {
                        logger.log(Level.SEVERE, "Database task hatası: " + t.getMessage(), t);
                    }
                    future.completeExceptionally(t);
                }
            });
        } catch (RejectedExecutionException ree) {
            future.completeExceptionally(ree);
        }
        return future;
    }

    public CompletableFuture<Void> runAsync(Runnable runnable) {
        Objects.requireNonNull(runnable, "runnable null olamaz.");
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
        this.closed = true;
        poolExecutor.shutdown();
        try {
            if (!poolExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                poolExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            poolExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}

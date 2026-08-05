package com.petsistemi.bootstrap;

import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * Test double for {@link MainThreadDispatcher} backed by a dedicated main test thread.
 *
 * <p>Actions submitted from a non-main thread (e.g. the DatabaseExecutor thread) are
 * queued and executed on the dedicated worker thread; they are never executed inline
 * on the caller thread. Reentrant submissions from the fake main thread itself are
 * executed inline, mirroring Bukkit's behavior.
 */
public class FakeMainThreadDispatcher implements MainThreadDispatcher {

    private final Thread fakeMainThread;
    private final Thread workerThread;
    private final BlockingQueue<Runnable> queue = new LinkedBlockingQueue<>();
    private final AtomicBoolean enabled = new AtomicBoolean(true);
    private final java.util.concurrent.atomic.AtomicLong submittedCount = new java.util.concurrent.atomic.AtomicLong();

    public FakeMainThreadDispatcher() {
        this.fakeMainThread = Thread.currentThread();
        this.workerThread = startWorker();
    }

    public FakeMainThreadDispatcher(Thread fakeMainThread) {
        this.fakeMainThread = Objects.requireNonNull(fakeMainThread, "fakeMainThread null olamaz.");
        this.workerThread = startWorker();
    }

    /** Total number of actions submitted via {@link #run} or {@link #supply} (including inline). */
    public long submittedCount() {
        return submittedCount.get();
    }

    private Thread startWorker() {
        Thread worker = new Thread(this::drainQueue, "fake-main-worker");
        worker.setDaemon(true);
        worker.start();
        return worker;
    }

    /** The dedicated thread that executes actions submitted from non-main threads. */
    public Thread getMainThread() {
        return workerThread;
    }

    public void setEnabled(boolean enabled) {
        this.enabled.set(enabled);
    }

    private void drainQueue() {
        while (true) {
            try {
                Runnable task = queue.take();
                try {
                    task.run();
                } catch (Throwable t) {
                    // The future wrapping the action already captured the exception;
                    // this guard prevents a failing action from killing the worker.
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    @Override
    public boolean isMainThread() {
        return Thread.currentThread() == fakeMainThread || Thread.currentThread() == workerThread;
    }

    @Override
    public CompletableFuture<Void> run(Runnable action) {
        Objects.requireNonNull(action, "action null olamaz.");
        submittedCount.incrementAndGet();
        if (!enabled.get()) {
            CompletableFuture<Void> failed = new CompletableFuture<>();
            failed.completeExceptionally(new IllegalStateException("Plugin devre dışı bırakıldı."));
            return failed;
        }

        if (isMainThread()) {
            return executeInline(() -> {
                action.run();
                return null;
            });
        }

        CompletableFuture<Void> future = new CompletableFuture<>();
        queue.add(() -> {
            try {
                action.run();
                future.complete(null);
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });
        return future;
    }

    @Override
    public <T> CompletableFuture<T> supply(Supplier<T> supplier) {
        Objects.requireNonNull(supplier, "supplier null olamaz.");
        submittedCount.incrementAndGet();
        if (!enabled.get()) {
            CompletableFuture<T> failed = new CompletableFuture<>();
            failed.completeExceptionally(new IllegalStateException("Plugin devre dışı bırakıldı."));
            return failed;
        }

        if (isMainThread()) {
            return executeInline(supplier);
        }

        CompletableFuture<T> future = new CompletableFuture<>();
        queue.add(() -> {
            try {
                future.complete(supplier.get());
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });
        return future;
    }

    private <T> CompletableFuture<T> executeInline(Supplier<T> supplier) {
        CompletableFuture<T> future = new CompletableFuture<>();
        try {
            future.complete(supplier.get());
        } catch (Throwable t) {
            future.completeExceptionally(t);
        }
        return future;
    }
}

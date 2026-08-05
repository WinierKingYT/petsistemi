package com.petsistemi.bootstrap;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public class FakeMainThreadDispatcher implements MainThreadDispatcher {

    private final Thread fakeMainThread;
    private boolean enabled = true;

    public FakeMainThreadDispatcher() {
        this.fakeMainThread = Thread.currentThread();
    }

    public FakeMainThreadDispatcher(Thread fakeMainThread) {
        this.fakeMainThread = fakeMainThread;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public boolean isMainThread() {
        return Thread.currentThread() == fakeMainThread;
    }

    @Override
    public CompletableFuture<Void> run(Runnable action) {
        Objects.requireNonNull(action, "action null olamaz.");
        CompletableFuture<Void> future = new CompletableFuture<>();

        if (!enabled) {
            future.completeExceptionally(new IllegalStateException("Plugin devre dışı bırakıldı."));
            return future;
        }

        try {
            action.run();
            future.complete(null);
        } catch (Throwable t) {
            future.completeExceptionally(t);
        }
        return future;
    }

    @Override
    public <T> CompletableFuture<T> supply(Supplier<T> supplier) {
        Objects.requireNonNull(supplier, "supplier null olamaz.");
        CompletableFuture<T> future = new CompletableFuture<>();

        if (!enabled) {
            future.completeExceptionally(new IllegalStateException("Plugin devre dışı bırakıldı."));
            return future;
        }

        try {
            future.complete(supplier.get());
        } catch (Throwable t) {
            future.completeExceptionally(t);
        }
        return future;
    }
}

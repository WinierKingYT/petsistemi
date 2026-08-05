package com.petsistemi.bootstrap;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public interface MainThreadDispatcher {
    CompletableFuture<Void> run(Runnable action);
    <T> CompletableFuture<T> supply(Supplier<T> supplier);
    boolean isMainThread();
}

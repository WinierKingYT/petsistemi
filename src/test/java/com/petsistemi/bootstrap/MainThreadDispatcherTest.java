package com.petsistemi.bootstrap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class MainThreadDispatcherTest {

    private FakeMainThreadDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        dispatcher = new FakeMainThreadDispatcher();
    }

    @Test
    void testActionExecutesOnMainThread() throws Exception {
        AtomicBoolean executed = new AtomicBoolean(false);
        CompletableFuture<Void> future = dispatcher.run(() -> executed.set(true));

        future.get();
        assertTrue(executed.get());
        assertTrue(dispatcher.isMainThread());
    }

    @Test
    void testDisabledDispatcherCompletesExceptionally() {
        dispatcher.setEnabled(false);
        CompletableFuture<Void> future = dispatcher.run(() -> {});

        ExecutionException ex = assertThrows(ExecutionException.class, future::get);
        assertTrue(ex.getCause().getMessage().contains("devre dışı"));
    }

    @Test
    void testActionExceptionPropagatesToFuture() {
        CompletableFuture<Void> future = dispatcher.run(() -> {
            throw new RuntimeException("Simulated Action Error");
        });

        ExecutionException ex = assertThrows(ExecutionException.class, future::get);
        assertEquals("Simulated Action Error", ex.getCause().getMessage());
    }
}

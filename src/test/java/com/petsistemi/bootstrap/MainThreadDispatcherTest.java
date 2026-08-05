package com.petsistemi.bootstrap;

import com.petsistemi.persistence.DatabaseExecutor;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class MainThreadDispatcherTest {

    private FakeMainThreadDispatcher dispatcher;
    private DatabaseExecutor dbExecutor;

    @BeforeEach
    void setUp() {
        dispatcher = new FakeMainThreadDispatcher();
        dbExecutor = new DatabaseExecutor(Logger.getLogger("MainThreadDispatcherTest"));
    }

    @AfterEach
    void tearDown() {
        if (dbExecutor != null && !dbExecutor.isClosed()) {
            dbExecutor.close();
        }
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

    @Test
    void testDbThreadSubmissionDoesNotRunInlineOnCallerThread() throws Exception {
        AtomicReference<Thread> executionThread = new AtomicReference<>();

        // Submit the action from the DatabaseExecutor thread, not the test thread
        CompletableFuture<Void> submitted = dbExecutor.submit(() -> {
            dispatcher.run(() -> executionThread.set(Thread.currentThread())).get();
            return null;
        });

        submitted.get(5, TimeUnit.SECONDS);

        assertNotNull(executionThread.get(), "Action MUST have executed.");
        assertNotEquals(Thread.currentThread(), executionThread.get(),
                "Action MUST NOT run inline on the caller thread.");
        assertNotEquals(Thread.currentThread().getName(), executionThread.get().getName());
    }

    @Test
    void testDbThreadSubmissionRunsOnDedicatedFakeMainThread() throws Exception {
        AtomicReference<Thread> executionThread = new AtomicReference<>();
        AtomicReference<Thread> callerThread = new AtomicReference<>();

        dbExecutor.submit(() -> {
            callerThread.set(Thread.currentThread());
            dispatcher.run(() -> executionThread.set(Thread.currentThread())).get();
            return null;
        }).get(5, TimeUnit.SECONDS);

        assertNotNull(executionThread.get(), "Action MUST have executed.");
        assertNotEquals(callerThread.get(), executionThread.get(),
                "Action MUST NOT run on the DatabaseExecutor thread.");
        assertEquals(dispatcher.getMainThread(), executionThread.get(),
                "Action MUST run on the dedicated fake main thread.");
    }
}

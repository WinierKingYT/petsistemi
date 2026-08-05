package com.petsistemi.persistence;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseExecutorTest {

    private DatabaseExecutor dbExecutor;

    @BeforeEach
    void setUp() {
        dbExecutor = new DatabaseExecutor(Logger.getLogger("TestDatabaseExecutor"));
    }

    @AfterEach
    void tearDown() {
        if (dbExecutor != null && !dbExecutor.isClosed()) {
            dbExecutor.close();
        }
    }

    @Test
    void testTaskRunsOnDatabaseThread() throws Exception {
        CompletableFuture<Boolean> future = dbExecutor.submit(dbExecutor::isDatabaseThread);
        assertTrue(future.get(5, TimeUnit.SECONDS));
    }

    @Test
    void testSingleThreadOrderingPreserved() throws Exception {
        List<Integer> executionOrder = Collections.synchronizedList(new ArrayList<>());
        for (int i = 1; i <= 10; i++) {
            final int index = i;
            dbExecutor.runAsync(() -> executionOrder.add(index));
        }

        CompletableFuture<Void> syncFuture = dbExecutor.runAsync(() -> {});
        syncFuture.get(5, TimeUnit.SECONDS);

        assertEquals(10, executionOrder.size());
        for (int i = 0; i < 10; i++) {
            assertEquals(i + 1, executionOrder.get(i));
        }
    }

    @Test
    void testExceptionTransferredToFuture() {
        CompletableFuture<String> future = dbExecutor.submit(() -> {
            throw new RuntimeException("DB SQL syntax error");
        });

        ExecutionException ex = assertThrows(ExecutionException.class, () -> future.get(5, TimeUnit.SECONDS));
        assertTrue(ex.getCause() instanceof RuntimeException);
        assertEquals("DB SQL syntax error", ex.getCause().getMessage());
    }

    @Test
    void testSubmitFailsAfterClose() {
        dbExecutor.close();
        assertTrue(dbExecutor.isClosed());

        CompletableFuture<String> future = dbExecutor.submit(() -> "success");
        ExecutionException ex = assertThrows(ExecutionException.class, () -> future.get(5, TimeUnit.SECONDS));
        assertTrue(ex.getCause() instanceof RejectedExecutionException);
    }

    @Test
    void testPendingTaskCount() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        dbExecutor.runAsync(() -> {
            try {
                latch.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        dbExecutor.runAsync(() -> {});
        dbExecutor.runAsync(() -> {});

        assertTrue(dbExecutor.pendingTaskCount() >= 1);
        latch.countDown();
    }
}

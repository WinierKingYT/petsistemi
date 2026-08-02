package com.petsistemi.persistence;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseExecutorTest {

    private DatabaseExecutor dbExecutor;

    @BeforeEach
    void setUp() {
        dbExecutor = new DatabaseExecutor(Logger.getLogger("TestLogger"));
    }

    @AfterEach
    void tearDown() {
        if (dbExecutor != null) {
            dbExecutor.close();
        }
    }

    @Test
    void testSubmitExecutesOnDedicatedThread() throws Exception {
        CompletableFuture<String> future = dbExecutor.submit(() -> Thread.currentThread().getName());
        String threadName = future.get();

        assertEquals("PetSistemi-Database-1", threadName);
    }

    @Test
    void testRunAsync() throws Exception {
        AtomicBoolean ran = new AtomicBoolean(false);
        CompletableFuture<Void> future = dbExecutor.runAsync(() -> ran.set(true));
        future.get();

        assertTrue(ran.get());
    }
}

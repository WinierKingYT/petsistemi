package com.petsistemi.application;

import com.petsistemi.api.result.PetDismissResult;
import com.petsistemi.bootstrap.FakeMainThreadDispatcher;
import com.petsistemi.persistence.DatabaseExecutor;
import com.petsistemi.persistence.PetRepository;
import com.petsistemi.persistence.PetSelectionRepository;
import com.petsistemi.persistence.PlayerPetProfileCache;
import com.petsistemi.runtime.ActivePet;
import com.petsistemi.runtime.ActivePetRegistry;
import com.petsistemi.runtime.PetRuntimeCoordinator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class DismissPipelineTest {

    private DatabaseExecutor dbExecutor;
    private FakeMainThreadDispatcher mainThreadDispatcher;
    private ActivePetRegistry activeRegistry;
    private UUID ownerId;
    private UUID petId;

    @BeforeEach
    void setUp() {
        dbExecutor = new DatabaseExecutor(Logger.getLogger("DismissPipelineTest"));
        mainThreadDispatcher = new FakeMainThreadDispatcher();
        ownerId = UUID.randomUUID();
        petId = UUID.randomUUID();
        activeRegistry = new ActivePetRegistry();
    }

    @AfterEach
    void tearDown() {
        if (dbExecutor != null && !dbExecutor.isClosed()) {
            dbExecutor.close();
        }
    }

    @Test
    void testDismissFailsWhenNoPetActive() throws Exception {
        PetRuntimeCoordinator coordinator = new PetRuntimeCoordinator(null, null, activeRegistry, null, null);
        PetRuntimeOperationService service = new PetRuntimeOperationService(
                null, null, null, null, coordinator, null, dbExecutor, mainThreadDispatcher
        );

        org.bukkit.entity.Player mockPlayer = org.mockito.Mockito.mock(org.bukkit.entity.Player.class);
        org.mockito.Mockito.when(mockPlayer.getUniqueId()).thenReturn(ownerId);

        CompletableFuture<PetDismissResult> future = service.dismissAsync(mockPlayer);
        PetDismissResult result = future.get(5, TimeUnit.SECONDS);

        assertFalse(result.success());
        assertTrue(result.message().contains("bulunmuyor"));
    }
}

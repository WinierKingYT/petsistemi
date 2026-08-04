package com.petsistemi.runtime;

import com.petsistemi.api.event.PetRecoveryFailedEvent;
import com.petsistemi.definition.PetDefinitionRegistry;
import com.petsistemi.domain.PetAvailabilityState;
import com.petsistemi.domain.PetDefinition;
import com.petsistemi.domain.PetInstance;
import com.petsistemi.persistence.PetRepository;
import com.petsistemi.runtime.recovery.PetRecoveryQueue;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PetWatchdogRecoveryTest {

    private PetRecoveryQueue recoveryQueue;

    @BeforeEach
    void setUp() {
        recoveryQueue = new PetRecoveryQueue();
    }

    @Test
    void testDuplicateRecoveryAttemptIsBlocked() {
        UUID ownerId = UUID.randomUUID();
        UUID petId = UUID.randomUUID();

        assertTrue(recoveryQueue.tryStart(ownerId, petId), "First tryStart must succeed");
        assertFalse(recoveryQueue.tryStart(ownerId, petId), "Duplicate tryStart for same pet MUST be blocked");
        assertTrue(recoveryQueue.isPending(petId), "Pet MUST be marked as pending recovery");

        recoveryQueue.clear(petId);
        assertFalse(recoveryQueue.isPending(petId), "Pet MUST NOT be pending after clear");
    }

    @Test
    void testRecoveryQueueExhaustion() {
        UUID ownerId = UUID.randomUUID();
        UUID petId = UUID.randomUUID();

        long now = System.currentTimeMillis() / 50;
        recoveryQueue.recordAttempt(ownerId, petId, now);
        assertFalse(recoveryQueue.isExhausted(petId));

        recoveryQueue.recordAttempt(ownerId, petId, now);
        assertFalse(recoveryQueue.isExhausted(petId));

        recoveryQueue.recordAttempt(ownerId, petId, now);
        assertTrue(recoveryQueue.isExhausted(petId), "Queue MUST be exhausted after 3 attempts");
    }
}

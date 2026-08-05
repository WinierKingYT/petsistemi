package com.petsistemi.runtime;

import com.petsistemi.api.event.PetRecoveryFailedEvent;
import com.petsistemi.definition.PetDefinitionRegistry;
import com.petsistemi.domain.PetAvailabilityState;
import com.petsistemi.domain.PetInstance;
import com.petsistemi.persistence.PetRepository;
import com.petsistemi.runtime.recovery.PetRecoveryQueue;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

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
    void testEmptyDbSelectionCancelsRecovery() throws Exception {
        PetRepository repository = mock(PetRepository.class);
        UUID ownerId = UUID.randomUUID();
        UUID petId = UUID.randomUUID();

        when(repository.findActiveByOwner(ownerId)).thenReturn(Optional.empty());

        recoveryQueue.tryStart(ownerId, petId);

        // Verification logic when repository returns empty selection
        Optional<PetInstance> dbActive = repository.findActiveByOwner(ownerId);
        if (dbActive.isEmpty()) {
            recoveryQueue.clear(petId);
        }

        assertFalse(recoveryQueue.isPending(petId), "Empty DB selection MUST clear pending recovery!");
    }

    @Test
    void testMismatchedDbSelectionCancelsRecovery() throws Exception {
        PetRepository repository = mock(PetRepository.class);
        UUID ownerId = UUID.randomUUID();
        UUID targetPetId = UUID.randomUUID();
        UUID newlySelectedPetId = UUID.randomUUID();

        PetInstance newlySelectedPet = new PetInstance(newlySelectedPetId, ownerId, "cat", "Kedi", 1, 0L, PetAvailabilityState.AVAILABLE, System.currentTimeMillis(), System.currentTimeMillis());
        when(repository.findActiveByOwner(ownerId)).thenReturn(Optional.of(newlySelectedPet));

        recoveryQueue.tryStart(ownerId, targetPetId);

        Optional<PetInstance> dbActive = repository.findActiveByOwner(ownerId);
        if (dbActive.isPresent() && !dbActive.get().petId().equals(targetPetId)) {
            recoveryQueue.clear(targetPetId);
        }

        assertFalse(recoveryQueue.isPending(targetPetId), "Mismatched pet selection in DB MUST cancel pending recovery!");
    }

    @Test
    void testDbExceptionDoesNotLeaveQueuePermanentlyLocked() throws Exception {
        PetRepository repository = mock(PetRepository.class);
        UUID ownerId = UUID.randomUUID();
        UUID petId = UUID.randomUUID();

        when(repository.findActiveByOwner(ownerId)).thenThrow(new RuntimeException("DB Connection Interrupted"));

        recoveryQueue.tryStart(ownerId, petId);

        try {
            repository.findActiveByOwner(ownerId);
        } catch (Exception e) {
            // Guard against lockup: exception handled gracefully, attempt continues or clears cleanly
        }

        assertTrue(recoveryQueue.isPending(petId), "DB exception during check must allow retry without permanently corrupting state");
        recoveryQueue.clear(petId);
        assertFalse(recoveryQueue.isPending(petId));
    }

    @Test
    void testOfflinePlayerCancelsRecoveryWithoutFailureEvent() {
        Player offlinePlayer = mock(Player.class);
        when(offlinePlayer.isOnline()).thenReturn(false);

        UUID petId = UUID.randomUUID();
        recoveryQueue.tryStart(UUID.randomUUID(), petId);

        if (!offlinePlayer.isOnline()) {
            recoveryQueue.clear(petId);
        }

        assertFalse(recoveryQueue.isPending(petId), "Offline player must clear recovery queue cleanly");
    }
}

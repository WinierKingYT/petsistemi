package com.petsistemi.listener;

import com.petsistemi.api.PetSnapshot;
import com.petsistemi.api.event.PetLevelUpEvent;
import com.petsistemi.domain.PetAvailabilityState;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PetLevelUpListenerTest {

    @Test
    void testLevelUpEventCreationAndGetters() {
        UUID petId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        PetSnapshot snapshot = new PetSnapshot(petId, ownerId, "wolf", "Kurtum", 5, 500, PetAvailabilityState.AVAILABLE, true, false);

        PetLevelUpEvent event = new PetLevelUpEvent(snapshot, 4, 5);

        assertEquals(snapshot, event.getPetSnapshot());
        assertEquals(4, event.getOldLevel());
        assertEquals(5, event.getNewLevel());
    }
}

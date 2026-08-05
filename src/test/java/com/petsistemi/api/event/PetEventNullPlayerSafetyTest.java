package com.petsistemi.api.event;

import com.petsistemi.api.PetSnapshot;
import com.petsistemi.domain.PetAvailabilityState;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that PetGiveEvent and PetRenameEvent can be constructed without a Player (async/system path).
 */
class PetEventNullPlayerSafetyTest {

    private static PetSnapshot snapshot() {
        return new PetSnapshot(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "wolf", "Kurt",
                1, 0,
                PetAvailabilityState.AVAILABLE,
                false, false
        );
    }

    // ── PetGiveEvent ──────────────────────────────────────────────────────────

    @Test
    void petGiveEventConstructedWithSnapshotOnlyDoesNotThrow() {
        PetSnapshot snap = snapshot();
        PetGiveEvent event = assertDoesNotThrow(() -> new PetGiveEvent(snap));
        assertEquals(snap.ownerId(), event.getOwnerId());
        assertSame(snap, event.getPet());
    }

    @Test
    void petGiveEventConstructedWithNullSnapshotDoesNotThrow() {
        PetGiveEvent event = assertDoesNotThrow(() -> new PetGiveEvent((PetSnapshot) null));
        assertNull(event.getOwnerId());
        assertNull(event.getPet());
    }

    @Test
    void petGiveEventWithExplicitOwnerIdAndNullPetDoesNotThrow() {
        UUID ownerId = UUID.randomUUID();
        PetGiveEvent event = assertDoesNotThrow(() -> new PetGiveEvent(ownerId, null));
        assertEquals(ownerId, event.getOwnerId());
        assertNull(event.getPet());
    }

    // ── PetRenameEvent ────────────────────────────────────────────────────────

    @Test
    void petRenameEventWithNullPlayerDoesNotThrow() {
        PetSnapshot snap = snapshot();
        PetRenameEvent event = assertDoesNotThrow(() -> new PetRenameEvent(null, snap, "Eski", "Yeni"));
        assertNull(event.getPlayer());
        assertEquals("Eski", event.getOldName());
        assertEquals("Yeni", event.getNewName());
        assertFalse(event.isCancelled());
    }

    @Test
    void petRenameEventTwoArgConstructorUsesSnapshotNameAsOldName() {
        PetSnapshot snap = snapshot();
        PetRenameEvent event = assertDoesNotThrow(() -> new PetRenameEvent(snap, "Fırtına"));
        assertNull(event.getPlayer());
        assertEquals(snap.customName(), event.getOldName());
        assertEquals("Fırtına", event.getNewName());
    }

    @Test
    void petRenameEventWithNullSnapshotDoesNotThrow() {
        PetRenameEvent event = assertDoesNotThrow(() -> new PetRenameEvent(null, "Yeni"));
        assertNull(event.getPlayer());
        assertNull(event.getOldName());
        assertNull(event.getPet());
    }

    @Test
    void petRenameEventCancelledStateIsChangeable() {
        PetRenameEvent event = new PetRenameEvent(snapshot(), "Fırtına");
        assertFalse(event.isCancelled());
        event.setCancelled(true);
        assertTrue(event.isCancelled());
    }

    @Test
    void petRenameEventNewNameIsChangeable() {
        PetRenameEvent event = new PetRenameEvent(snapshot(), "İlk");
        event.setNewName("Değiştirildi");
        assertEquals("Değiştirildi", event.getNewName());
    }
}
